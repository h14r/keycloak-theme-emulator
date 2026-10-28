package kcemu;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Simulierte Server-Daten: Realm-Einstellungen, Nutzer, Client, Identity-Provider.
 * Entspricht dem, was in der Admin-Konsole eingestellt ist. Wird aus scenario.json gelesen
 * und kann in der Oberfläche live geändert werden.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Scenario {

    public static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Login-Theme des Realms (Realm settings → Themes → Login theme). */
    public String loginTheme = "keycloak.v2";
    /** E-Mail-Theme des Realms (Realm settings → Themes → Email theme). */
    public String emailTheme = "keycloak";
    /** Accept-Language-Header des simulierten Browsers. */
    public String acceptLanguage = "de-DE,de;q=0.9,en;q=0.8";

    public RealmData realm = new RealmData();
    public UserData user = new UserData();
    public ClientData client = new ClientData();
    public List<IdpData> identityProviders = new ArrayList<>();
    /**
     * Optional: User-Profile-Konfiguration des Realms (Realm settings → User profile → JSON editor).
     * Fehlt sie, gilt die Keycloak-Standardkonfiguration.
     */
    public JsonNode userProfile;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RealmData {
        public String name = "demo";
        public String displayName = "Demo";
        public String displayNameHtml;
        public boolean registrationAllowed = true;
        public boolean registrationEmailAsUsername = false;
        public boolean resetPasswordAllowed = true;
        public boolean rememberMe = true;
        public boolean loginWithEmailAllowed = true;
        public boolean duplicateEmailsAllowed = false;
        public boolean editUsernameAllowed = false;
        public boolean verifyEmail = true;
        public boolean internationalizationEnabled = true;
        public List<String> supportedLocales = new ArrayList<>(List.of("de", "en"));
        public String defaultLocale = "de";
        /** Passwort-Richtlinie im Admin-Format, z. B. "length(12) and digits(1)". Nur für Anzeige-Beans. */
        public String passwordPolicy = "length(8)";
        /** Realm-Attribut darkMode (Realm settings → Themes → Dark mode). */
        public boolean darkMode = true;
        public boolean organizationsEnabled = false;
        /** Realm settings → Localization → Realm overrides: Sprache → (Schlüssel → Text). */
        public Map<String, Map<String, String>> localizationTexts = new LinkedHashMap<>();
        public Map<String, String> attributes = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserData {
        public String username = "max.mustermann";
        public String email = "max.mustermann@example.org";
        public String firstName = "Max";
        public String lastName = "Mustermann";
        public boolean emailVerified = true;
        /** Bezeichnungen der eingerichteten OTP-Geräte (für login-otp.ftl). */
        public List<String> otpDevices = new ArrayList<>(List.of("Smartphone"));
        public Map<String, List<String>> attributes = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientData {
        public String clientId = "portal";
        public String name = "Kundenportal";
        public String description;
        public String baseUrl = "https://portal.example.org";
        public Map<String, String> attributes = new LinkedHashMap<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IdpData {
        public String alias;
        public String providerId;
        public String displayName;
        public String guiOrder;
        public boolean enabled = true;
        public boolean hideOnLogin = false;
    }

    public static Scenario load(File file) throws IOException {
        if (file != null && file.isFile()) {
            return JSON.readValue(file, Scenario.class);
        }
        return new Scenario();
    }

    public Scenario copy() {
        return JSON.convertValue(JSON.valueToTree(this), Scenario.class);
    }
}
