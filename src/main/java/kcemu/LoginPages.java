package kcemu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.FreeMarkerLoginFormsProvider;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.UserProfile;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import kcemu.env.Emulator;
import kcemu.env.Emulator.Env;

/**
 * Katalog der Formularseiten des Login-Themes.
 *
 * Jede Variante ruft denselben Einstiegspunkt von {@link FreeMarkerLoginFormsProvider} auf,
 * den auch der jeweilige Authenticator / die Required Action im Server benutzt, und setzt
 * Meldungen mit denselben Schlüsseln und Feldern (Quelle jeweils im Kommentar).
 */
public final class LoginPages {

    private LoginPages() {
    }

    @FunctionalInterface
    public interface Render {
        Response render(Env env, FreeMarkerLoginFormsProvider forms) throws Exception;
    }

    public static final class Variant {
        public final String id;
        public final String label;
        final Render render;
        final Consumer<Emulator.Options> options;

        Variant(String id, String label, Consumer<Emulator.Options> options, Render render) {
            this.id = id;
            this.label = label;
            this.options = options;
            this.render = render;
        }
    }

    public static final class Page {
        public final String id;
        public final String template;
        public final String label;
        public final String group;
        public final String trigger;
        public final List<Variant> variants = new ArrayList<>();

        Page(String id, String template, String label, String group, String trigger) {
            this.id = id;
            this.template = template;
            this.label = label;
            this.group = group;
            this.trigger = trigger;
        }

        Page v(String id, String label, Render r) {
            variants.add(new Variant(id, label, o -> { }, r));
            return this;
        }

        Page v(String id, String label, Consumer<Emulator.Options> opt, Render r) {
            variants.add(new Variant(id, label, opt, r));
            return this;
        }

        public Variant variant(String vid) {
            for (Variant v : variants) {
                if (v.id.equals(vid)) {
                    return v;
                }
            }
            return variants.get(0);
        }
    }

    public static final Map<String, Page> PAGES = new LinkedHashMap<>();

    private static Page page(String id, String template, String label, String group, String trigger) {
        Page p = new Page(id, template, label, group, trigger);
        PAGES.put(id, p);
        return p;
    }

    /** Vor der Anmeldung ist der Nutzer noch nicht bekannt. */
    private static final Consumer<Emulator.Options> ANONYMOUS = o -> o.userLoggedIn = false;

    private static MultivaluedMap<String, String> form(String... kv) {
        MultivaluedMap<String, String> m = new MultivaluedMapImpl<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.putSingle(kv[i], kv[i + 1]);
        }
        return m;
    }

    static {
        // ---------------------------------------------------------------- Anmeldung
        page("login", "login.ftl", "Anmeldung", "Anmeldung",
                "Browser-Flow → Username Password Form (UsernamePasswordForm)")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .createLoginUsernamePassword())
            // AbstractUsernameFormAuthenticator: challenge(context, Messages.INVALID_USER, FIELD_USERNAME)
            .v("invalid", "Falsche Anmeldedaten", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .setFormData(form("username", e.sc.user.username))
                    .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.INVALID_USER))
                    .createLoginUsernamePassword())
            // AbstractUsernameFormAuthenticator: challenge(context, Messages.ACCOUNT_DISABLED)
            .v("disabled", "Konto deaktiviert", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .setFormData(form("username", e.sc.user.username))
                    .setError(Messages.ACCOUNT_DISABLED)
                    .createLoginUsernamePassword())
            // Brute-Force-Schutz: disabledByBruteForceError(..) im Feld username
            .v("locked", "Vorübergehend gesperrt (Brute Force)", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .setFormData(form("username", e.sc.user.username))
                    .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.ACCOUNT_TEMPORARILY_DISABLED))
                    .createLoginUsernamePassword())
            // ResetCredentialEmail: context.forkWithSuccessMessage(new FormMessage(Messages.EMAIL_SENT))
            .v("reset-sent", "Nach „Passwort vergessen“: E-Mail gesendet", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .setSuccess(Messages.EMAIL_SENT)
                    .createLoginUsernamePassword())
            .v("timeout", "Sitzung abgelaufen (loginTimeout)", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .setError(Messages.LOGIN_TIMEOUT)
                    .createLoginUsernamePassword());

        page("login-username", "login-username.ftl", "Anmeldung – nur Benutzername", "Anmeldung",
                "Browser-Flow mit Username Form (UsernameForm)")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .createLoginUsername())
            // UsernameForm#getDefaultChallengeMessage
            .v("invalid", "Unbekannter Benutzer", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .setFormData(form("username", "unbekannt"))
                    .addError(new FormMessage(Validation.FIELD_USERNAME,
                            e.realm.isLoginWithEmailAllowed() ? Messages.INVALID_USERNAME_OR_EMAIL : Messages.INVALID_USERNAME))
                    .createLoginUsername());

        page("login-password", "login-password.ftl", "Anmeldung – nur Passwort", "Anmeldung",
                "Browser-Flow mit Password Form (PasswordForm), Nutzer bereits identifiziert")
            .v("standard", "Standard", (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .createLoginPassword())
            // PasswordForm#getDefaultChallengeMessage, Feld password
            .v("invalid", "Falsches Passwort", (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .addError(new FormMessage(Validation.FIELD_PASSWORD, Messages.INVALID_PASSWORD))
                    .createLoginPassword());

        page("login-otp", "login-otp.ftl", "Einmalcode (OTP)", "Anmeldung",
                "Browser-Flow → OTP Form (OTPFormAuthenticator)")
            .v("standard", "Standard", (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .createLoginTotp())
            // OTPFormAuthenticator: challenge(context, Messages.INVALID_TOTP, Validation.FIELD_OTP_CODE)
            .v("invalid", "Falscher Code", (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .addError(new FormMessage(Validation.FIELD_OTP_CODE, Messages.INVALID_TOTP))
                    .createLoginTotp());

        page("login-page-expired", "login-page-expired.ftl", "Seite abgelaufen", "Anmeldung",
                "Zurück-Taste / veralteter Tab im Anmeldevorgang")
            // AuthenticationFlowURLHelper#showPageExpired: setActionUri(lastStepUrl).setExecution(..)
            .v("standard", "Standard", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("authenticate")).setExecution("emu-execution")
                    .createLoginExpiredPage());

        // ---------------------------------------------------------------- Registrierung
        page("register", "register.ftl", "Registrierung", "Registrierung",
                "Link „Registrieren“ (Registration-Flow, Felder aus dem User Profile)")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> registration(e, f, false).createRegistration())
            .v("terms", "Mit Nutzungsbedingungen", ANONYMOUS, (e, f) -> registration(e, f, true).createRegistration())
            // RegistrationUserCreation: profile.validate() → Validation.getFormErrorsFromValidation(..)
            // RegistrationPassword: MISSING_PASSWORD
            .v("errors", "Fehlerhafte Eingaben (echte Profil-Validierung)", ANONYMOUS, (e, f) -> {
                MultivaluedMap<String, String> data = form(
                        "username", "", "email", "keine-gueltige-adresse", "firstName", "", "lastName", "Mustermann");
                List<FormMessage> errors = new ArrayList<>();
                try {
                    UserProfile profile = e.session.getProvider(UserProfileProvider.class)
                            .create(UserProfileContext.REGISTRATION, data);
                    profile.validate();
                } catch (ValidationException pve) {
                    errors.addAll(Validation.getFormErrorsFromValidation(pve.getErrors()));
                }
                errors.add(new FormMessage(Validation.FIELD_PASSWORD, Messages.MISSING_PASSWORD));
                return registration(e, f, false).setFormData(data).setErrors(errors).createRegistration();
            });

        page("terms", "terms.ftl", "Nutzungsbedingungen", "Registrierung",
                "Required Action „Terms and Conditions“ (TermsAndConditions)")
            .v("standard", "Standard", (e, f) -> f
                    .setActionUri(e.actionUri("required-action")).createForm("terms.ftl"));

        // ---------------------------------------------------------------- Passwort
        page("login-reset-password", "login-reset-password.ftl", "Passwort vergessen", "Passwort",
                "Link „Passwort vergessen?“ (Reset-Credentials-Flow, ResetCredentialChooseUser)")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("reset-credentials")).setExecution("emu-execution")
                    .createPasswordReset())
            // ResetCredentialChooseUser: addError(new FormMessage(FIELD_USERNAME, Messages.MISSING_USERNAME))
            .v("missing", "Kein Benutzername eingegeben", ANONYMOUS, (e, f) -> f
                    .setActionUri(e.actionUri("reset-credentials")).setExecution("emu-execution")
                    .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.MISSING_USERNAME))
                    .createPasswordReset());

        page("login-update-password", "login-update-password.ftl", "Neues Passwort festlegen", "Passwort",
                "Required Action „Update Password“ bzw. Link aus der Passwort-Reset-Mail")
            .v("standard", "Standard (über Reset-Link)", (e, f) -> f
                    .setActionUri(e.actionUri("required-action")).createResponse(UserModel.RequiredAction.UPDATE_PASSWORD))
            .v("admin", "Vom Admin angefordert", (e, f) -> {
                e.user.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
                return f.setActionUri(e.actionUri("required-action")).createResponse(UserModel.RequiredAction.UPDATE_PASSWORD);
            })
            // UpdatePassword: addError(new FormMessage(FIELD_PASSWORD_CONFIRM, Messages.NOTMATCH_PASSWORD))
            .v("mismatch", "Passwörter stimmen nicht überein", (e, f) -> f
                    .setActionUri(e.actionUri("required-action"))
                    .addError(new FormMessage(Validation.FIELD_PASSWORD_CONFIRM, Messages.NOTMATCH_PASSWORD))
                    .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD))
            .v("policy", "Verstoß gegen Passwort-Richtlinie", (e, f) -> f
                    .setActionUri(e.actionUri("required-action"))
                    .setError("invalidPasswordMinLengthMessage", 8)
                    .createResponse(UserModel.RequiredAction.UPDATE_PASSWORD));

        // ---------------------------------------------------------------- Konto / Required Actions
        page("login-update-profile", "login-update-profile.ftl", "Profil vervollständigen", "Konto",
                "Required Action „Update Profile“ / „Verify Profile“ (Felder aus dem User Profile)")
            .v("standard", "Standard", (e, f) -> f
                    .setActionUri(e.actionUri("required-action")).createResponse(UserModel.RequiredAction.UPDATE_PROFILE))
            .v("empty", "Neuer Nutzer ohne Namen", (e, f) -> {
                e.user.setFirstName(null);
                e.user.setLastName(null);
                return f.setActionUri(e.actionUri("required-action")).createResponse(UserModel.RequiredAction.UPDATE_PROFILE);
            });

        page("login-verify-email", "login-verify-email.ftl", "E-Mail bestätigen", "Konto",
                "Required Action „Verify Email“")
            .v("standard", "Standard", (e, f) -> {
                e.user.setEmailVerified(false);
                return f.setActionUri(e.actionUri("required-action")).createResponse(UserModel.RequiredAction.VERIFY_EMAIL);
            });

        page("login-config-totp", "login-config-totp.ftl", "Authenticator einrichten (OTP)", "Konto",
                "Required Action „Configure OTP“")
            .v("standard", "Standard", (e, f) -> f
                    .setActionUri(e.actionUri("required-action")).createResponse(UserModel.RequiredAction.CONFIGURE_TOTP))
            .v("invalid", "Falscher Code", (e, f) -> f
                    .setActionUri(e.actionUri("required-action"))
                    .addError(new FormMessage(Validation.FIELD_OTP_CODE, Messages.INVALID_TOTP))
                    .createResponse(UserModel.RequiredAction.CONFIGURE_TOTP));

        page("delete-account-confirm", "delete-account-confirm.ftl", "Konto löschen bestätigen", "Konto",
                "Required Action „Delete Account“")
            .v("standard", "Standard", (e, f) -> f
                    .setActionUri(e.actionUri("required-action"))
                    .setAttribute("triggered_from_aia", false)
                    .createForm("delete-account-confirm.ftl"))
            .v("aia", "Aus der Anwendung ausgelöst (AIA)", (e, f) -> f
                    .setActionUri(e.actionUri("required-action"))
                    .setAttribute("triggered_from_aia", true)
                    .createForm("delete-account-confirm.ftl"));

        page("login-idp-link-confirm", "login-idp-link-confirm.ftl", "Konto verknüpfen?", "Identity Provider",
                "First-Broker-Login: Konto mit gleicher E-Mail existiert bereits")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> brokered(e, f).createIdpLinkConfirmLinkPage());

        page("login-idp-link-email", "login-idp-link-email.ftl", "Verknüpfung per E-Mail bestätigen", "Identity Provider",
                "First-Broker-Login: Bestätigung per E-Mail")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> brokered(e, f).createIdpLinkEmailPage());

        // ---------------------------------------------------------------- Info & Fehler
        page("info", "info.ftl", "Info-Seite", "Info & Fehler", "Abschluss von Aktionen (z. B. Link aus E-Mail)")
            .v("updated", "Konto aktualisiert", (e, f) -> f.setSuccess(Messages.ACCOUNT_UPDATED).createInfoPage())
            .v("verified", "E-Mail bestätigt", (e, f) -> f.setSuccess(Messages.EMAIL_VERIFIED).createInfoPage())
            .v("actions", "Aktionen ausführen (Admin-Mail)", (e, f) -> f
                    .setAttribute("requiredActions", List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"))
                    .setInfo(Messages.CONFIRM_EXECUTION_OF_ACTIONS)
                    .setAttribute("actionUri", e.actionUri("action-token").toString())
                    .createInfoPage());

        page("error", "error.ftl", "Fehlerseite", "Info & Fehler", "Fehler im Anmeldevorgang (createErrorPage)")
            .v("expired", "Link abgelaufen", (e, f) -> f.setError(Messages.EXPIRED_ACTION_TOKEN_NO_SESSION)
                    .createErrorPage(Response.Status.BAD_REQUEST))
            .v("cookie", "Cookie nicht gefunden", ANONYMOUS, (e, f) -> f.setError(Messages.COOKIE_NOT_FOUND)
                    .createErrorPage(Response.Status.BAD_REQUEST))
            .v("invalid", "Ungültige Anfrage", ANONYMOUS, (e, f) -> f.setError(Messages.INVALID_REQUEST)
                    .createErrorPage(Response.Status.BAD_REQUEST))
            .v("notfound", "Seite nicht gefunden", ANONYMOUS, (e, f) -> f.setError(Messages.PAGE_NOT_FOUND)
                    .createErrorPage(Response.Status.NOT_FOUND));

        page("logout-confirm", "logout-confirm.ftl", "Abmelden bestätigen", "Info & Fehler",
                "RP-initiated Logout ohne id_token_hint")
            .v("standard", "Standard", (e, f) -> f.setClientSessionCode("emuCode").createLogoutConfirmPage());

        page("login-oauth2-device-verify-user-code", "login-oauth2-device-verify-user-code.ftl",
                "Gerätecode eingeben", "Info & Fehler", "OAuth 2.0 Device Authorization Grant")
            .v("standard", "Standard", ANONYMOUS, (e, f) -> f.createOAuth2DeviceVerifyUserCodePage());
    }

    /**
     * Wie FormAuthenticationFlow#renderForm: Action-URI, Execution und die buildPage(..)-Beiträge
     * der Formular-Schritte des Registrierungs-Flows (RegistrationPassword setzt passwordRequired,
     * RegistrationTermsAndConditions – falls im Flow aktiviert – termsAcceptanceRequired).
     */
    private static LoginFormsProvider registration(Env e, FreeMarkerLoginFormsProvider f, boolean terms) {
        f.setActionUri(e.actionUri("registration")).setExecution("emu-execution")
                .setAttribute("passwordRequired", true);
        if (terms) {
            f.setAttribute("termsAcceptanceRequired", true);
        }
        return f;
    }

    /** Wie IdpCreateUserIfUniqueAuthenticator / IdpConfirmLinkAuthenticator: Broker-Kontext als Attribut. */
    private static LoginFormsProvider brokered(Env e, FreeMarkerLoginFormsProvider f) {
        IdentityProviderModel idp = new IdentityProviderModel();
        Scenario.IdpData d = e.sc.identityProviders.isEmpty() ? null : e.sc.identityProviders.get(0);
        idp.setAlias(d == null ? "google" : d.alias);
        idp.setProviderId(d == null ? "google" : d.providerId);
        idp.setDisplayName(d == null ? "Google" : d.displayName);
        idp.setEnabled(true);
        BrokeredIdentityContext ctx = new BrokeredIdentityContext("emu-broker-id", idp);
        ctx.setUsername(e.sc.user.username);
        ctx.setEmail(e.sc.user.email);
        ctx.setFirstName(e.sc.user.firstName);
        ctx.setLastName(e.sc.user.lastName);
        return f.setActionUri(e.actionUri("first-broker-login"))
                .setAttribute(LoginFormsProvider.IDENTITY_PROVIDER_BROKER_CONTEXT, ctx)
                .setAttribute("brokerContext", ctx);
    }

    public static Response render(Emulator emu, Page page, Variant v, String kcLocale, String theme, List<Env> envOut) throws Exception {
        Emulator.Options o = new Emulator.Options();
        o.kcLocale = kcLocale;
        o.loginTheme = theme;
        v.options.accept(o);
        Env env = emu.newEnv(o);
        envOut.add(env);
        return v.render.render(env, env.loginForms());
    }
}
