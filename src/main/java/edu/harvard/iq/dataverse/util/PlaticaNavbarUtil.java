package edu.harvard.iq.dataverse.util;

import edu.harvard.iq.dataverse.settings.JvmSettings;
import edu.harvard.iq.dataverse.util.json.JsonUtil;
import jakarta.json.JsonArrayBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for the PLATICA common header widget ({@code <navbar-root>}), which replaces the right
 * section of the navbar (language selector, login and account menu) when its script URL is configured.
 *
 * The Keycloak location used by the browser to obtain the widget's access token is derived from the
 * existing OIDC provider setting ({@code dataverse.auth.oidc.auth-server-url}), so only the public
 * client id has to be configured separately.
 */
public final class PlaticaNavbarUtil {

    static final String DEFAULT_PROFILE_URL = "https://platica-web-pre.rediris.es/es/mi-cuenta";
    // Same default as DataverseSession uses for the locale cookie
    static final String DEFAULT_LOCALE_COOKIE_NAME = "ui_locale";
    private static final String REALMS_SEGMENT = "/realms/";

    private PlaticaNavbarUtil() {
    }

    /**
     * Keycloak server base URL and realm name, e.g. {@code https://auth.example.org} and {@code platica}.
     */
    public record KeycloakLocation(String url, String realm) {
    }

    public static Optional<String> getNavbarScriptUrl() {
        return nonBlank(JvmSettings.UI_PLATICA_NAVBAR_SCRIPT_URL.lookupOptional());
    }

    public static boolean isNavbarEnabled() {
        return getNavbarScriptUrl().isPresent();
    }

    public static String getProfileUrl() {
        return nonBlank(JvmSettings.UI_PLATICA_PROFILE_URL.lookupOptional()).orElse(DEFAULT_PROFILE_URL);
    }

    public static Optional<String> getKeycloakClientId() {
        return nonBlank(JvmSettings.UI_PLATICA_KEYCLOAK_CLIENT_ID.lookupOptional());
    }

    public static Optional<KeycloakLocation> getKeycloakLocation() {
        return nonBlank(JvmSettings.OIDC_AUTH_SERVER_URL.lookupOptional())
                .flatMap(PlaticaNavbarUtil::parseAuthServerUrl);
    }

    public static String getLocaleCookieName() {
        return nonBlank(JvmSettings.UI_LOCALE_COOKIE_NAME.lookupOptional()).orElse(DEFAULT_LOCALE_COOKIE_NAME);
    }

    public static Optional<String> getLocaleCookieDomain() {
        return nonBlank(JvmSettings.UI_LOCALE_COOKIE_DOMAIN.lookupOptional());
    }

    /**
     * Splits an OIDC issuer URL like {@code https://auth.example.org/realms/platica/} into the Keycloak
     * base URL and the realm name. Returns empty if the URL does not follow the Keycloak layout.
     */
    static Optional<KeycloakLocation> parseAuthServerUrl(String authServerUrl) {
        if (authServerUrl == null) {
            return Optional.empty();
        }
        String trimmed = authServerUrl.trim().replaceAll("/+$", "");
        int index = trimmed.lastIndexOf(REALMS_SEGMENT);
        if (index <= 0) {
            return Optional.empty();
        }
        String url = trimmed.substring(0, index);
        String realm = trimmed.substring(index + REALMS_SEGMENT.length());
        if (realm.isEmpty() || realm.contains("/")) {
            return Optional.empty();
        }
        return Optional.of(new KeycloakLocation(url, realm));
    }

    /**
     * One entry of the widget's profile menu. {@code icon} is a Heroicons outline name supported by the
     * widget (e.g. {@code eye}, {@code document-text}, {@code squares-2x2}). External items open in a new tab.
     */
    public record MenuItem(String id, String label, String icon, String url, boolean external) {
    }

    /**
     * Serializes menu items in the widget's {@code menu-items} format. They all go in the "custom" group,
     * like the items OpenCDMP adds.
     */
    public static String toMenuItemsJson(List<MenuItem> items) {
        JsonArrayBuilder array = JsonUtil.createArrayBuilder();
        for (MenuItem item : items) {
            array.add(JsonUtil.createObjectBuilder()
                    .add("id", item.id())
                    .add("label", item.label())
                    .add("icon", item.icon())
                    .add("url", item.url())
                    .add("isExternal", item.external())
                    .add("group", "custom"));
        }
        return array.build().toString();
    }

    /**
     * Serializes the configured locales (e.g. {@code es -> Español}) in the widget's {@code languages} format:
     * the upper-case code as label and the lower-case code as value, as OpenCDMP does.
     */
    public static String toLanguagesJson(Map<String, String> configuredLocales) {
        JsonArrayBuilder array = JsonUtil.createArrayBuilder();
        for (String code : configuredLocales.keySet()) {
            array.add(JsonUtil.createObjectBuilder()
                    .add("label", code.toUpperCase(Locale.ROOT))
                    .add("value", code.toLowerCase(Locale.ROOT)));
        }
        return array.build().toString();
    }

    static Optional<String> nonBlank(Optional<String> value) {
        return value.map(String::trim).filter(s -> !s.isEmpty());
    }
}
