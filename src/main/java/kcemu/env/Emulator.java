package kcemu.env;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.specimpl.ResteasyHttpHeaders;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.component.ComponentModel;
import org.keycloak.cookie.CookieProvider;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.validate.Validator;
import org.keycloak.validate.ValidatorFactory;
import org.keycloak.credential.CredentialModel;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.locale.DefaultLocaleSelectorProvider;
import org.keycloak.locale.LocaleSelectorProvider;
import org.keycloak.locale.LocaleUpdaterProvider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderStorageProvider;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakUriInfo;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RequiredCredentialModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.ThemeManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.policy.PasswordPolicyProvider;
import org.keycloak.policy.PasswordPolicyProviderFactory;
import org.keycloak.protocol.ClientData;
import org.keycloak.protocol.LoginProtocol;
import org.keycloak.services.util.LocaleUtil;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;
import org.keycloak.storage.adapter.InMemoryUserAdapter;
import org.keycloak.theme.ClasspathThemeProvider;
import org.keycloak.theme.DefaultThemeManager;
import org.keycloak.theme.DefaultThemeManagerFactory;
import org.keycloak.theme.DefaultThemeSelectorProvider;
import org.keycloak.theme.FolderThemeProvider;
import org.keycloak.theme.JarThemeProviderFactory;
import org.keycloak.theme.Theme;
import org.keycloak.theme.ThemeProvider;
import org.keycloak.theme.ThemeResourceProvider;
import org.keycloak.theme.ThemeSelectorProvider;
import org.keycloak.theme.freemarker.FreeMarkerProvider;
import org.keycloak.tracing.TracingProvider;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.userprofile.DeclarativeUserProfileProvider;
import org.keycloak.userprofile.DeclarativeUserProfileProviderFactory;
import org.keycloak.userprofile.UserProfileProvider;

import jakarta.ws.rs.core.MultivaluedMap;
import kcemu.Scenario;

/**
 * Baut für jeden Render-Vorgang eine Keycloak-Session zusammen.
 *
 * Echt (aus keycloak-services / keycloak-server-spi-private der eingestellten Version):
 * Theme-Auswahl, Theme-Manager mit parent/import-Auflösung, Folder- und Classpath-Themes,
 * FreeMarker-Provider, Locale-Auflösung, User-Profile, Passwort-Richtlinien, Nutzerobjekt.
 *
 * Simuliert: Realm, Client, Authentifizierungssession, Identity-Provider-Liste, Credentials,
 * HTTP-Anfrage und alles, was im Server eine Datenbank oder Infinispan bräuchte.
 */
public class Emulator {

    public static final String PROTOCOL = "openid-connect";
    public static final String TAB_ID = "emuTab01";

    private static boolean globalInit;

    public final File themesDir;
    public final String baseUrl;
    private volatile Scenario scenario;

    private final DefaultThemeManagerFactory themeManagerFactory;
    private final ThemeProvider classpathThemes;
    private final DeclarativeUserProfileProviderFactory userProfileFactory;
    private final Map<String, PasswordPolicyProviderFactory> passwordPolicyFactories = new HashMap<>();
    private final Map<String, ValidatorFactory> validatorFactories = new HashMap<>();
    private final Map<String, CredentialProviderFactory<?>> credentialProviderFactories = new HashMap<>();

    public Emulator(File themesDir, Scenario scenario, String baseUrl) {
        initGlobal();
        this.themesDir = themesDir;
        this.scenario = scenario;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.themeManagerFactory = new DefaultThemeManagerFactory();
        JarThemeProviderFactory jar = new JarThemeProviderFactory();
        jar.init(null);
        this.classpathThemes = jar.create(null);
        this.userProfileFactory = new DeclarativeUserProfileProviderFactory();
        this.userProfileFactory.init(Config.scope("userProfile", DeclarativeUserProfileProviderFactory.ID));
        load(PasswordPolicyProviderFactory.class, f -> passwordPolicyFactories.put(f.getId(), f));
        load(ValidatorFactory.class, f -> validatorFactories.put(f.getId(), f));
        load(CredentialProviderFactory.class, f -> credentialProviderFactories.put(f.getId(), f));
    }

    /** Lädt Provider-Fabriken wie der Server; Fabriken mit hier fehlenden Laufzeit-Abhängigkeiten werden übersprungen. */
    private static <T> void load(Class<T> type, java.util.function.Consumer<T> sink) {
        java.util.Iterator<T> it = ServiceLoader.load(type).iterator();
        while (true) {
            try {
                if (!it.hasNext()) {
                    return;
                }
                sink.accept(it.next());
            } catch (java.util.ServiceConfigurationError | LinkageError e) {
                // z. B. Metrik-Abhängigkeiten, die nur im Quarkus-Server vorhanden sind
            }
        }
    }

    /**
     * Server-Konfiguration wie bei "kc.sh start-dev": Theme- und Template-Cache aus,
     * damit Änderungen am Theme sofort sichtbar sind. Feature-Flags in der Voreinstellung.
     */
    private static synchronized void initGlobal() {
        if (globalInit) {
            return;
        }
        System.setProperty("keycloak.theme.cacheThemes", "false");
        System.setProperty("keycloak.theme.cacheTemplates", "false");
        System.setProperty("keycloak.theme.staticMaxAge", "-1");
        Config.init(new Config.SystemPropertiesConfigProvider());
        Profile.defaults();
        globalInit = true;
    }

    public Scenario scenario() {
        return scenario;
    }

    public void setScenario(Scenario s) {
        this.scenario = s;
    }

    /** Optionen eines einzelnen Render-Vorgangs. */
    public static class Options {
        /** wie ?kc_locale=… im Browser; null = Auflösung über Accept-Language/Realm-Standard */
        public String kcLocale;
        /** Login-Seiten laufen in einer Authentifizierungssession, E-Mails außerhalb eines Requests evtl. nicht */
        public boolean withAuthSession = true;
        /** Pfad hinter /realms/{realm}/ der simulierten Anfrage */
        public String requestPath = "login-actions/authenticate";
        public boolean userLoggedIn = true;
        /** Abweichendes Login-/E-Mail-Theme nur für diesen Aufruf (z. B. Vergleichsansicht) */
        public String loginTheme;
        public String emailTheme;
    }

    public Env newEnv(Options opt) {
        Scenario sc = scenario.copy();
        if (opt.loginTheme != null && !opt.loginTheme.isBlank()) {
            sc.loginTheme = opt.loginTheme;
        }
        if (opt.emailTheme != null && !opt.emailTheme.isBlank()) {
            sc.emailTheme = opt.emailTheme;
        }
        return new Env(sc, opt);
    }

    /** Eine vollständig verdrahtete Session für genau einen Render-Vorgang. */
    public class Env {
        public final Scenario sc;
        public final KeycloakSession session;
        public final KeycloakContext context;
        public final RealmModel realm;
        public final ClientModel client;
        public final UserModel user;
        public final AuthenticationSessionModel authSession;
        public final InstrumentedFreeMarker freeMarker = new InstrumentedFreeMarker();
        public final List<String> warnings = new ArrayList<>();
        public final List<SentMail> sentMails = new ArrayList<>();
        public final KeycloakUriInfo uriInfo;

        private final Map<String, Object> sessionAttributes = new HashMap<>();
        private final Map<String, String> authNotes = new LinkedHashMap<>();
        private final Map<String, String> clientNotes = new LinkedHashMap<>();

        Env(Scenario sc, Options opt) {
            this.sc = sc;
            this.session = Mocks.mock(KeycloakSession.class);
            this.context = Mocks.mock(KeycloakContext.class);
            this.realm = buildRealm();
            this.client = buildClient();
            ClientModel account = Mocks.mock(ClientModel.class);
            doReturn("client-account").when(account).getId();
            doReturn("account").when(account).getClientId();
            doReturn("${client_account}").when(account).getName();
            doReturn(true).when(account).isEnabled();
            doReturn(realm).when(account).getRealm();
            doAnswer(inv -> {
                String id = inv.getArgument(0);
                return sc.client.clientId.equals(id) ? client : "account".equals(id) ? account : null;
            }).when(realm).getClientByClientId(nullable(String.class));

            doAnswer(inv -> provider(inv.getArgument(0))).when(session).getProvider(any(Class.class));
            doAnswer(inv -> namedProvider(inv.getArgument(0), inv.getArgument(1))).when(session).getProvider(any(Class.class), nullable(String.class));

            // --- HTTP-Anfrage ---------------------------------------------------------------
            String query = "client_id=" + sc.client.clientId + "&tab_id=" + TAB_ID
                    + (opt.kcLocale != null && !opt.kcLocale.isBlank() ? "&kc_locale=" + opt.kcLocale : "");
            String requestUri = baseUrl + "realms/" + sc.realm.name + "/" + opt.requestPath + "?" + query;
            // Antwort nach Methodenname – die Schnittstelle unterscheidet sich zwischen Keycloak-Versionen
            URI base = URI.create(baseUrl);
            HostnameProvider hostname = org.mockito.Mockito.mock(HostnameProvider.class, inv -> switch (inv.getMethod().getName()) {
                case "getBaseUri" -> base;
                case "getScheme" -> base.getScheme();
                case "getHostname" -> base.getHost();
                case "getPort" -> base.getPort();
                case "getContextPath" -> "/";
                default -> null;
            });
            providers.put(HostnameProvider.class, hostname);
            this.uriInfo = new KeycloakUriInfo(session, org.keycloak.urls.UrlType.FRONTEND,
                    new ResteasyUriInfo(requestUri, "/"));
            MultivaluedMap<String, String> headers = new MultivaluedMapImpl<>();
            if (sc.acceptLanguage != null && !sc.acceptLanguage.isBlank()) {
                headers.putSingle("Accept-Language", sc.acceptLanguage);
            }
            headers.putSingle("Accept", "text/html");
            ResteasyHttpHeaders httpHeaders = new ResteasyHttpHeaders(headers);
            HttpRequest request = Mocks.mock(HttpRequest.class);
            doReturn(uriInfo).when(request).getUri();
            doReturn(httpHeaders).when(request).getHttpHeaders();
            doReturn(new MultivaluedMapImpl<String, String>()).when(request).getDecodedFormParameters();

            // --- Nutzer ---------------------------------------------------------------------
            this.user = opt.userLoggedIn ? buildUser() : null;

            // --- Authentifizierungssession --------------------------------------------------
            this.authSession = opt.withAuthSession ? buildAuthSession() : null;

            // --- Kontext --------------------------------------------------------------------
            doReturn(realm).when(context).getRealm();
            doReturn(client).when(context).getClient();
            doReturn(uriInfo).when(context).getUri();
            doReturn(uriInfo).when(context).getUri(any());
            doReturn(httpHeaders).when(context).getRequestHeaders();
            doReturn(request).when(context).getHttpRequest();
            doReturn(authSession).when(context).getAuthenticationSession();
            doReturn(URI.create(baseUrl)).when(context).getAuthServerUrl();
            doReturn("/").when(context).getContextPath();
            doAnswer(inv -> provider(LocaleSelectorProvider.class).resolveLocale(realm, inv.getArgument(0)))
                    .when(context).resolveLocale(nullable(UserModel.class));
            doAnswer(inv -> provider(LocaleSelectorProvider.class).resolveLocale(realm, inv.getArgument(0), (boolean) inv.getArgument(1)))
                    .when(context).resolveLocale(nullable(UserModel.class), anyBoolean());
            doAnswer(inv -> provider(LocaleSelectorProvider.class).resolveLocale(realm, inv.getArgument(0)))
                    .when(context).resolveLocale(nullable(UserModel.class), any(Theme.Type.class));

            // --- Session --------------------------------------------------------------------
            doReturn(context).when(session).getContext();
            ThemeManager themeManager = new DefaultThemeManager(themeManagerFactory, session);
            doReturn(themeManager).when(session).theme();
            doAnswer(inv -> allProviders(inv.getArgument(0))).when(session).getAllProviders(any(Class.class));
            doAnswer(inv -> sessionAttributes.get((String) inv.getArgument(0))).when(session).getAttribute(anyString());
            doAnswer(inv -> sessionAttributes.get((String) inv.getArgument(0))).when(session).getAttribute(anyString(), any(Class.class));
            doAnswer(inv -> sessionAttributes.put(inv.getArgument(0), inv.getArgument(1))).when(session).setAttribute(anyString(), any());
            doAnswer(inv -> sessionAttributes.remove((String) inv.getArgument(0))).when(session).removeAttribute(anyString());
            doReturn(sessionAttributes).when(session).getAttributes();
            KeycloakSessionFactory sessionFactory = Mocks.mock(KeycloakSessionFactory.class);
            doAnswer(inv -> providerFactory(inv.getArgument(0), inv.getArgument(1)))
                    .when(sessionFactory).getProviderFactory(any(Class.class), nullable(String.class));
            doReturn(sessionFactory).when(session).getKeycloakSessionFactory();
            doReturn(buildUsers()).when(session).users();
            doReturn(buildIdps()).when(session).identityProviders();

            providers.put(FreeMarkerProvider.class, freeMarker);
            providers.put(ThemeSelectorProvider.class, new DefaultThemeSelectorProvider(session));
            providers.put(LocaleSelectorProvider.class, new DefaultLocaleSelectorProvider(session));
            providers.put(LocaleUpdaterProvider.class, Mocks.mock(LocaleUpdaterProvider.class));
            providers.put(CookieProvider.class, Mocks.mock(CookieProvider.class));
            providers.put(TracingProvider.class, Mocks.mock(TracingProvider.class));
            providers.put(UserProfileProvider.class, userProfileFactory.create(session));
            providers.put(EmailSenderProvider.class, new CapturingEmailSender(sentMails));

            // Passwort-Richtlinie erst jetzt – sie braucht die PasswordPolicyProvider der Session
            PasswordPolicy policy;
            try {
                policy = sc.realm.passwordPolicy == null || sc.realm.passwordPolicy.isBlank()
                        ? PasswordPolicy.empty()
                        : PasswordPolicy.parse(session, sc.realm.passwordPolicy);
            } catch (RuntimeException e) {
                warnings.add("Passwort-Richtlinie \"" + sc.realm.passwordPolicy + "\" ungültig: " + e.getMessage());
                policy = PasswordPolicy.empty();
            }
            doReturn(policy).when(realm).getPasswordPolicy();

            // wie LoginActionsService: ?kc_locale wird in der Authentifizierungssession gemerkt
            LocaleUtil.processLocaleParam(session, realm, authSession);
        }

        private final Map<Class<?>, Object> providers = new HashMap<>();

        @SuppressWarnings("unchecked")
        <T> T provider(Class<T> type) {
            Object p = providers.get(type);
            if (p == null && type == LoginProtocol.class) {
                return (T) namedProvider(type, PROTOCOL);
            }
            return (T) p;
        }

        private Object namedProvider(Class<?> type, String id) {
            if (type == LoginProtocol.class) {
                LoginProtocol protocol = Mocks.mock(LoginProtocol.class);
                ClientData cd = new ClientData(sc.client.baseUrl, "code", "query", "emu-state");
                doReturn(cd).when(protocol).getClientData(any());
                return protocol;
            }
            if (type == PasswordPolicyProvider.class) {
                PasswordPolicyProviderFactory f = passwordPolicyFactories.get(id);
                return f == null ? null : f.create(session);
            }
            if (type == Validator.class) {
                ValidatorFactory f = validatorFactories.get(id);
                return f == null ? null : f.create(session);
            }
            if (type == CredentialProvider.class) {
                CredentialProviderFactory<?> f = credentialProviderFactories.get(id);
                return f == null ? null : f.create(session);
            }
            if (type == UserProfileProvider.class || id == null) {
                return provider(type);
            }
            return providers.get(type);
        }

        private Object providerFactory(Class<?> type, String id) {
            if (type == Validator.class) {
                return validatorFactories.get(id);
            }
            if (type == PasswordPolicyProvider.class) {
                return passwordPolicyFactories.get(id);
            }
            if (type == CredentialProvider.class) {
                return credentialProviderFactories.get(id);
            }
            return null;
        }

        private Set<?> allProviders(Class<?> type) {
            if (type == ThemeProvider.class) {
                // Reihenfolge wie im Server: Ordner-Themes (Priorität 100) vor mitgelieferten Themes
                return new java.util.LinkedHashSet<>(List.of(new FolderThemeProvider(themesDir), classpathThemes));
            }
            if (type == ThemeResourceProvider.class) {
                return Set.of();
            }
            return Set.of();
        }

        public FreeMarkerLoginFormsProvider loginForms() {
            FreeMarkerLoginFormsProvider forms = new FreeMarkerLoginFormsProvider(session);
            if (authSession != null) {
                forms.setAuthenticationSession(authSession);
            }
            if (user != null) {
                forms.setUser(user);
            }
            return forms;
        }

        public URI actionUri(String action) {
            return URI.create(baseUrl + "realms/" + sc.realm.name + "/login-actions/" + action
                    + "?session_code=emuCode&execution=emu-execution&client_id=" + sc.client.clientId + "&tab_id=" + TAB_ID);
        }

        // ------------------------------------------------------------------------------------

        private RealmModel buildRealm() {
            Scenario.RealmData r = sc.realm;
            RealmModel m = Mocks.mock(RealmModel.class);
            doReturn("realm-" + r.name).when(m).getId();
            doReturn(r.name).when(m).getName();
            doReturn(blankToNull(r.displayName)).when(m).getDisplayName();
            doReturn(blankToNull(r.displayNameHtml)).when(m).getDisplayNameHtml();
            doReturn(true).when(m).isEnabled();
            doReturn(r.registrationAllowed).when(m).isRegistrationAllowed();
            doReturn(r.registrationEmailAsUsername).when(m).isRegistrationEmailAsUsername();
            doReturn(r.resetPasswordAllowed).when(m).isResetPasswordAllowed();
            doReturn(r.rememberMe).when(m).isRememberMe();
            doReturn(r.loginWithEmailAllowed).when(m).isLoginWithEmailAllowed();
            doReturn(r.duplicateEmailsAllowed).when(m).isDuplicateEmailsAllowed();
            doReturn(r.editUsernameAllowed).when(m).isEditUsernameAllowed();
            doReturn(r.verifyEmail).when(m).isVerifyEmail();
            doReturn(r.organizationsEnabled).when(m).isOrganizationsEnabled();
            doReturn(r.internationalizationEnabled).when(m).isInternationalizationEnabled();
            doAnswer(inv -> r.supportedLocales.stream()).when(m).getSupportedLocalesStream();
            doReturn(r.defaultLocale).when(m).getDefaultLocale();
            doReturn(sc.loginTheme).when(m).getLoginTheme();
            doReturn(sc.emailTheme).when(m).getEmailTheme();
            doReturn(true).when(m).isIdentityFederationEnabled();
            doAnswer(inv -> Stream.of(RequiredCredentialModel.PASSWORD)).when(m).getRequiredCredentialsStream();
            doReturn(300).when(m).getActionTokenGeneratedByUserLifespan();
            doReturn(300).when(m).getActionTokenGeneratedByUserLifespan(anyString());
            doReturn(43200).when(m).getActionTokenGeneratedByAdminLifespan();
            doReturn(1800).when(m).getAccessCodeLifespanLogin();
            doReturn(300).when(m).getAccessCodeLifespanUserAction();
            doReturn(OTPPolicy.DEFAULT_POLICY).when(m).getOTPPolicy();
            doReturn(new HashMap<String, String>(Map.of("from", "noreply@example.org", "fromDisplayName", r.displayName == null ? r.name : r.displayName)))
                    .when(m).getSmtpConfig();

            Map<String, String> attrs = new HashMap<>(r.attributes);
            attrs.put("darkMode", String.valueOf(r.darkMode));
            doAnswer(inv -> attrs.get((String) inv.getArgument(0))).when(m).getAttribute(anyString());
            doReturn(attrs).when(m).getAttributes();

            doAnswer(inv -> localizationFor(inv.getArgument(0))).when(m).getRealmLocalizationTextsByLocale(nullable(String.class));
            doReturn(r.localizationTexts).when(m).getRealmLocalizationTexts();

            if (sc.userProfile != null && !sc.userProfile.isNull()) {
                ComponentModel c = new ComponentModel();
                c.setId("emu-user-profile");
                c.setName(DeclarativeUserProfileProviderFactory.ID);
                c.setProviderId(DeclarativeUserProfileProviderFactory.ID);
                c.setProviderType(UserProfileProvider.class.getName());
                c.setParentId("realm-" + r.name);
                c.getConfig().putSingle(DeclarativeUserProfileProvider.UP_COMPONENT_CONFIG_KEY, sc.userProfile.toString());
                doAnswer(inv -> Stream.of(c)).when(m).getComponentsStream(anyString(), eq(UserProfileProvider.class.getName()));
            }
            return m;
        }

        private Map<String, String> localizationFor(String tag) {
            if (tag == null) {
                return Map.of();
            }
            Map<String, String> m = sc.realm.localizationTexts.get(tag);
            return m == null ? Map.of() : m;
        }

        private ClientModel buildClient() {
            Scenario.ClientData c = sc.client;
            ClientModel m = Mocks.mock(ClientModel.class);
            doReturn("client-" + c.clientId).when(m).getId();
            doReturn(c.clientId).when(m).getClientId();
            doReturn(blankToNull(c.name)).when(m).getName();
            doReturn(blankToNull(c.description)).when(m).getDescription();
            doReturn(blankToNull(c.baseUrl)).when(m).getBaseUrl();
            doReturn(true).when(m).isEnabled();
            doReturn(PROTOCOL).when(m).getProtocol();
            doReturn(realm).when(m).getRealm();
            doAnswer(inv -> c.attributes.get((String) inv.getArgument(0))).when(m).getAttribute(anyString());
            doReturn(new HashMap<>(c.attributes)).when(m).getAttributes();
            return m;
        }

        private UserModel buildUser() {
            Scenario.UserData u = sc.user;
            SubjectCredentialManager creds = buildCredentials();
            InMemoryUserAdapter m = new InMemoryUserAdapter(session, realm, "user-emu-1") {
                @Override
                public SubjectCredentialManager credentialManager() {
                    return creds;
                }
            };
            m.setUsername(u.username);
            m.setEmail(u.email);
            m.setFirstName(u.firstName);
            m.setLastName(u.lastName);
            m.setEmailVerified(u.emailVerified);
            m.setEnabled(true);
            m.setCreatedTimestamp(System.currentTimeMillis());
            u.attributes.forEach(m::setAttribute);
            return m;
        }

        private AuthenticationSessionModel buildAuthSession() {
            AuthenticationSessionModel a = Mocks.mock(AuthenticationSessionModel.class);
            RootAuthenticationSessionModel root = Mocks.mock(RootAuthenticationSessionModel.class);
            doReturn("emu-root-session").when(root).getId();
            doReturn(realm).when(root).getRealm();
            doReturn(root).when(a).getParentSession();
            doReturn(TAB_ID).when(a).getTabId();
            doReturn(realm).when(a).getRealm();
            doReturn(client).when(a).getClient();
            doReturn(PROTOCOL).when(a).getProtocol();
            doReturn(sc.client.baseUrl).when(a).getRedirectUri();
            doReturn(AuthenticationSessionModel.Action.AUTHENTICATE.name()).when(a).getAction();
            doAnswer(inv -> user).when(a).getAuthenticatedUser();
            doAnswer(inv -> authNotes.get((String) inv.getArgument(0))).when(a).getAuthNote(anyString());
            doAnswer(inv -> authNotes.put(inv.getArgument(0), inv.getArgument(1))).when(a).setAuthNote(anyString(), nullable(String.class));
            doAnswer(inv -> authNotes.remove((String) inv.getArgument(0))).when(a).removeAuthNote(anyString());
            doAnswer(inv -> clientNotes.get((String) inv.getArgument(0))).when(a).getClientNote(anyString());
            doAnswer(inv -> clientNotes.put(inv.getArgument(0), inv.getArgument(1))).when(a).setClientNote(anyString(), nullable(String.class));
            doAnswer(inv -> clientNotes.remove((String) inv.getArgument(0))).when(a).removeClientNote(anyString());
            doReturn(clientNotes).when(a).getClientNotes();
            return a;
        }

        public void setAuthNote(String key, String value) {
            authNotes.put(key, value);
        }

        public void setClientNote(String key, String value) {
            clientNotes.put(key, value);
        }

        private UserProvider buildUsers() {
            return Mocks.mock(UserProvider.class);
        }

        private SubjectCredentialManager buildCredentials() {
            SubjectCredentialManager creds = Mocks.mock(SubjectCredentialManager.class);
            List<CredentialModel> otp = new ArrayList<>();
            int i = 0;
            for (String label : sc.user.otpDevices) {
                CredentialModel c = new CredentialModel();
                c.setId("otp-" + (++i));
                c.setType(OTPCredentialModel.TYPE);
                c.setUserLabel(label);
                c.setCreatedDate(System.currentTimeMillis());
                c.setCredentialData("{\"subType\":\"totp\",\"digits\":6,\"counter\":0,\"period\":30,\"algorithm\":\"HmacSHA1\"}");
                c.setSecretData("{\"value\":\"emu\"}");
                otp.add(c);
            }
            doAnswer(inv -> OTPCredentialModel.TYPE.equals(inv.getArgument(0)) ? otp.stream() : Stream.empty())
                    .when(creds).getStoredCredentialsByTypeStream(anyString());
            doAnswer(inv -> otp.stream()).when(creds).getStoredCredentialsStream();
            doAnswer(inv -> OTPCredentialModel.TYPE.equals(inv.getArgument(0)) ? !otp.isEmpty() : "password".equals(inv.getArgument(0)))
                    .when(creds).isConfiguredFor(anyString());
            return creds;
        }

        private IdentityProviderStorageProvider buildIdps() {
            IdentityProviderStorageProvider idps = Mocks.mock(IdentityProviderStorageProvider.class);
            List<IdentityProviderModel> models = new ArrayList<>();
            for (Scenario.IdpData d : sc.identityProviders) {
                IdentityProviderModel m = new IdentityProviderModel();
                m.setAlias(d.alias);
                m.setInternalId("idp-" + d.alias);
                m.setProviderId(d.providerId == null ? d.alias : d.providerId);
                m.setDisplayName(blankToNull(d.displayName));
                m.setEnabled(d.enabled);
                m.setHideOnLogin(d.hideOnLogin);
                m.setLinkOnly(false);
                Map<String, String> cfg = new HashMap<>(m.getConfig());
                if (d.guiOrder != null) {
                    cfg.put("guiOrder", d.guiOrder);
                }
                cfg.put("hideOnLogin", String.valueOf(d.hideOnLogin));
                m.setConfig(cfg);
                models.add(m);
            }
            doAnswer(inv -> models.stream().filter(IdentityProviderStorageProvider.LoginFilter.getLoginPredicate()))
                    .when(idps).getForLogin(any(), nullable(String.class));
            doAnswer(inv -> models.stream().filter(x -> x.getAlias().equals(inv.getArgument(0))).findFirst().orElse(null))
                    .when(idps).getByAlias(anyString());
            doAnswer(inv -> models.stream()).when(idps).getAllStream();
            return idps;
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Erfasst, was der echte E-Mail-Provider versenden würde, statt es per SMTP zu senden. */
    public static class SentMail {
        public String to;
        public String subject;
        public String textBody;
        public String htmlBody;
        public Map<String, String> smtpConfig;
    }

    static class CapturingEmailSender implements EmailSenderProvider {
        private final List<SentMail> out;

        CapturingEmailSender(List<SentMail> out) {
            this.out = out;
        }

        @Override
        public void send(Map<String, String> config, UserModel user, String subject, String textBody, String htmlBody) {
            send(config, user == null ? null : user.getEmail(), subject, textBody, htmlBody);
        }

        @Override
        public void send(Map<String, String> config, String address, String subject, String textBody, String htmlBody) {
            SentMail m = new SentMail();
            m.to = address;
            m.subject = subject;
            m.textBody = textBody;
            m.htmlBody = htmlBody;
            m.smtpConfig = config;
            out.add(m);
        }

        @Override
        public void validate(Map<String, String> config) {
        }

        @Override
        public void close() {
        }
    }

    public ThemeProvider classpathThemes() {
        return classpathThemes;
    }

    public static boolean isClasspathProvider(ThemeProvider p) {
        return p instanceof ClasspathThemeProvider;
    }
}
