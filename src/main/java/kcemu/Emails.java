package kcemu;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProvider;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.Constants;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.OrganizationModel;

import kcemu.env.Emulator;
import kcemu.env.Emulator.Env;
import kcemu.env.Mocks;

import static org.mockito.Mockito.doReturn;

/**
 * Katalog der E-Mails des E-Mail-Themes. Jede Mail wird über dieselbe Methode von
 * {@link FreeMarkerEmailTemplateProvider} erzeugt, die der Server benutzt (Quelle im Kommentar);
 * der SMTP-Versand ist durch einen Mitschnitt ersetzt.
 */
public final class Emails {

    private Emails() {
    }

    @FunctionalInterface
    interface Send {
        void send(Env env, EmailTemplateProvider p) throws Exception;
    }

    public static final class Mail {
        public final String id;
        public final String template;
        public final String label;
        public final String trigger;
        final Send send;

        Mail(String id, String template, String label, String trigger, Send send) {
            this.id = id;
            this.template = template;
            this.label = label;
            this.trigger = trigger;
            this.send = send;
        }
    }

    public static final Map<String, Mail> MAILS = new LinkedHashMap<>();

    private static void mail(String id, String template, String label, String trigger, Send s) {
        MAILS.put(id, new Mail(id, template, label, trigger, s));
    }

    private static String link(Env e, String path) {
        return withSlash(e.uriInfo.getBaseUri().toString()) + "realms/" + e.sc.realm.name + "/login-actions/" + path
                + "?key=eyJhbGciOiJIUzUxMiJ9.emulator-token&client_id=" + e.sc.client.clientId + "&tab_id=" + Emulator.TAB_ID;
    }

    static String withSlash(String s) {
        return s.endsWith("/") ? s : s + "/";
    }

    private static Event event(Env e, EventType type, Map<String, String> details) {
        Event ev = new Event();
        ev.setId("emu-event");
        ev.setType(type);
        ev.setTime(System.currentTimeMillis());
        ev.setRealmId(e.realm.getId());
        ev.setRealmName(e.realm.getName());
        ev.setClientId(e.sc.client.clientId);
        ev.setUserId(e.user.getId());
        ev.setIpAddress("203.0.113.42");
        ev.setDetails(details);
        return ev;
    }

    static {
        // VerifyEmail#sendVerifyEmail → sendVerifyEmail(link, expirationInMinutes)
        mail("email-verification", "email-verification.ftl", "E-Mail-Adresse bestätigen",
                "Required Action „Verify Email“ / Admin: „Send verify email“",
                (e, p) -> p.setAuthenticationSession(e.authSession)
                        .sendVerifyEmail(link(e, "action-token"), 5));

        // ResetCredentialEmail → sendPasswordReset(link, expirationInMinutes)
        mail("password-reset", "password-reset.ftl", "Passwort zurücksetzen",
                "„Passwort vergessen?“ auf der Login-Seite",
                (e, p) -> p.setAuthenticationSession(e.authSession)
                        .sendPasswordReset(link(e, "action-token"), 5));

        // UserResource#executeActionsEmail → requiredActions + IGNORE_ACCEPT_LANGUAGE_HEADER
        mail("executeActions", "executeActions.ftl", "Konto aktualisieren (Admin-Aktionen)",
                "Admin-Konsole: Users → Credentials → „Credential Reset“ / „Send email“",
                (e, p) -> p.setAttribute(Constants.TEMPLATE_ATTR_REQUIRED_ACTIONS, List.of("UPDATE_PASSWORD", "VERIFY_EMAIL", "CONFIGURE_TOTP"))
                        .setAttribute(Constants.IGNORE_ACCEPT_LANGUAGE_HEADER, true)
                        .sendExecuteActions(link(e, "action-token"), 720));

        // UpdateEmail → sendEmailUpdateConfirmation(link, exp, newEmail)
        mail("email-update-confirmation", "email-update-confirmation.ftl", "Neue E-Mail-Adresse bestätigen",
                "Required Action „Update Email“ mit Bestätigung",
                (e, p) -> p.setAuthenticationSession(e.authSession)
                        .sendEmailUpdateConfirmation(link(e, "action-token"), 5, "neu." + e.sc.user.email));

        // IdpEmailVerificationAuthenticator → sendConfirmIdentityBrokerLink(link, exp)
        mail("identity-provider-link", "identity-provider-link.ftl", "Konto mit Identity Provider verknüpfen",
                "First-Broker-Login: Verknüpfung per E-Mail bestätigen",
                (e, p) -> {
                    Scenario.IdpData d = e.sc.identityProviders.isEmpty() ? null : e.sc.identityProviders.get(0);
                    IdentityProviderModel idp = new IdentityProviderModel();
                    idp.setAlias(d == null ? "google" : d.alias);
                    idp.setProviderId(d == null ? "google" : d.providerId);
                    idp.setDisplayName(d == null ? "Google" : d.displayName);
                    idp.setEnabled(true);
        BrokeredIdentityContext ctx = new BrokeredIdentityContext("emu-broker-id", idp);
                    ctx.setUsername(e.sc.user.username);
                    ctx.setEmail(e.sc.user.email);
                    p.setAuthenticationSession(e.authSession)
                            .setAttribute(EmailTemplateProvider.IDENTITY_PROVIDER_BROKER_CONTEXT, ctx)
                            .sendConfirmIdentityBrokerLink(link(e, "action-token"), 5);
                });

        // OrganizationInvitationResource → sendOrgInviteEmail(org, link, exp)
        mail("org-invite", "org-invite.ftl", "Einladung in eine Organisation",
                "Admin: Organizations → Members → „Invite member“",
                (e, p) -> {
                    OrganizationModel org = Mocks.mock(OrganizationModel.class);
                    doReturn("org-1").when(org).getId();
                    doReturn("Beispiel AG").when(org).getName();
                    doReturn("beispiel-ag").when(org).getAlias();
                    p.sendOrgInviteEmail(org, link(e, "action-token"), 43200 / 60);
                });

        // RealmAdminResource#testSMTPConnection → sendSmtpTestEmail(config, user)
        mail("email-test", "email-test.ftl", "SMTP-Testnachricht",
                "Admin: Realm settings → Email → „Test connection“",
                (e, p) -> p.sendSmtpTestEmail(e.realm.getSmtpConfig(), e.user));

        // EmailEventListenerProvider → sendEvent(event) (Event-Listener „email“ im Realm aktiv)
        mail("event-login_error", "event-login_error.ftl", "Ereignis: Fehlgeschlagene Anmeldung",
                "Event-Listener „email“, Ereignis LOGIN_ERROR",
                (e, p) -> p.sendEvent(event(e, EventType.LOGIN_ERROR, new HashMap<>())));
        mail("event-update_password", "event-update_password.ftl", "Ereignis: Passwort geändert",
                "Event-Listener „email“, Ereignis UPDATE_PASSWORD",
                (e, p) -> p.sendEvent(event(e, EventType.UPDATE_PASSWORD, new HashMap<>())));
        mail("event-update_totp", "event-update_totp.ftl", "Ereignis: OTP eingerichtet",
                "Event-Listener „email“, Ereignis UPDATE_TOTP",
                (e, p) -> p.sendEvent(event(e, EventType.UPDATE_TOTP, new HashMap<>())));
        mail("event-remove_totp", "event-remove_totp.ftl", "Ereignis: OTP entfernt",
                "Event-Listener „email“, Ereignis REMOVE_TOTP",
                (e, p) -> p.sendEvent(event(e, EventType.REMOVE_TOTP, new HashMap<>())));
        mail("event-update_credential", "event-update_credential.ftl", "Ereignis: Anmeldeverfahren geändert",
                "Event-Listener „email“, Ereignis UPDATE_CREDENTIAL",
                (e, p) -> p.sendEvent(event(e, EventType.UPDATE_CREDENTIAL, new HashMap<>(Map.of("credential_type", "otp")))));
        mail("event-remove_credential", "event-remove_credential.ftl", "Ereignis: Anmeldeverfahren entfernt",
                "Event-Listener „email“, Ereignis REMOVE_CREDENTIAL",
                (e, p) -> p.sendEvent(event(e, EventType.REMOVE_CREDENTIAL, new HashMap<>(Map.of("credential_type", "otp")))));
        mail("event-user_disabled_by_temporary_lockout", "event-user_disabled_by_temporary_lockout.ftl",
                "Ereignis: Konto vorübergehend gesperrt", "Brute-Force-Erkennung (temporäre Sperre)",
                (e, p) -> p.sendEvent(event(e, EventType.USER_DISABLED_BY_TEMPORARY_LOCKOUT, new HashMap<>())));
        mail("event-user_disabled_by_permanent_lockout", "event-user_disabled_by_permanent_lockout.ftl",
                "Ereignis: Konto dauerhaft gesperrt", "Brute-Force-Erkennung (dauerhafte Sperre)",
                (e, p) -> p.sendEvent(event(e, EventType.USER_DISABLED_BY_PERMANENT_LOCKOUT, new HashMap<>())));
    }

    /** Ergebnis eines Mail-Renderings. */
    public static final class Result {
        public Env env;
        public Emulator.SentMail mail;
        public Throwable error;
    }

    public static Result render(Emulator emu, Mail m, String locale, String theme) {
        Emulator.Options o = new Emulator.Options();
        o.emailTheme = theme;
        Result r = new Result();
        Env env = emu.newEnv(o);
        r.env = env;
        if (locale != null && !locale.isBlank()) {
            // Mails werden in der Sprache des Nutzers verschickt (Attribut „locale“ im Profil)
            env.user.setSingleAttribute("locale", locale);
        }
        FreeMarkerEmailTemplateProvider p = new FreeMarkerEmailTemplateProvider(env.session);
        p.setRealm(env.realm).setUser(env.user);
        try {
            m.send.send(env, p);
        } catch (Exception ex) {
            r.error = ex;
        }
        if (!env.sentMails.isEmpty()) {
            r.mail = env.sentMails.get(env.sentMails.size() - 1);
        }
        return r;
    }
}
