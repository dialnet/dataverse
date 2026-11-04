package edu.harvard.iq.dataverse.util;

import edu.harvard.iq.dataverse.settings.JvmSettings;
import edu.harvard.iq.dataverse.util.PlaticaNavbarUtil.KeycloakLocation;
import edu.harvard.iq.dataverse.util.PlaticaNavbarUtil.MenuItem;
import edu.harvard.iq.dataverse.util.json.JsonUtil;
import edu.harvard.iq.dataverse.util.testing.JvmSetting;
import edu.harvard.iq.dataverse.util.testing.LocalJvmSettings;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lookups are only tested with {@link JvmSetting} (system properties), which take precedence over other
 * config sources such as a local {@code .env} file, so the results don't depend on the developer's setup.
 * Default handling is tested through the pure helpers instead of the live configuration.
 */
@LocalJvmSettings
class PlaticaNavbarUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void nonBlankTreatsBlankAsMissing(String value) {
        assertTrue(PlaticaNavbarUtil.nonBlank(Optional.of(value)).isEmpty());
        assertTrue(PlaticaNavbarUtil.nonBlank(Optional.empty()).isEmpty());
    }

    @Test
    void nonBlankTrimsValues() {
        assertEquals(Optional.of("webapp"), PlaticaNavbarUtil.nonBlank(Optional.of("  webapp ")));
    }

    @Test
    void defaultsAreThePreValues() {
        assertEquals("https://platica-web-pre.rediris.es/es/mi-cuenta", PlaticaNavbarUtil.DEFAULT_PROFILE_URL);
        // Must stay in line with the default used by DataverseSession for the locale cookie
        assertEquals("ui_locale", PlaticaNavbarUtil.DEFAULT_LOCALE_COOKIE_NAME);
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_PLATICA_NAVBAR_SCRIPT_URL, value = "   ")
    void navbarIsDisabledWhenScriptUrlIsBlank() {
        assertFalse(PlaticaNavbarUtil.isNavbarEnabled());
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_PLATICA_NAVBAR_SCRIPT_URL, value = " https://web.example.org/sas/core-navbar/v1.0.0/shared-navbar.esm.js ")
    void navbarIsEnabledAndScriptUrlIsTrimmed() {
        assertTrue(PlaticaNavbarUtil.isNavbarEnabled());
        assertEquals(Optional.of("https://web.example.org/sas/core-navbar/v1.0.0/shared-navbar.esm.js"),
                PlaticaNavbarUtil.getNavbarScriptUrl());
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_PLATICA_PROFILE_URL, value = "   ")
    void blankProfileUrlFallsBackToDefault() {
        assertEquals(PlaticaNavbarUtil.DEFAULT_PROFILE_URL, PlaticaNavbarUtil.getProfileUrl());
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_PLATICA_PROFILE_URL, value = "https://web.example.org/en/my-account")
    void profileUrlCanBeOverridden() {
        assertEquals("https://web.example.org/en/my-account", PlaticaNavbarUtil.getProfileUrl());
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_PLATICA_KEYCLOAK_CLIENT_ID, value = "webapp")
    void keycloakClientIdIsRead() {
        assertEquals(Optional.of("webapp"), PlaticaNavbarUtil.getKeycloakClientId());
    }

    @Test
    @JvmSetting(key = JvmSettings.OIDC_AUTH_SERVER_URL, value = "https://auth.example.org/realms/platica")
    void keycloakLocationIsDerivedFromOidcSetting() {
        assertEquals(Optional.of(new KeycloakLocation("https://auth.example.org", "platica")),
                PlaticaNavbarUtil.getKeycloakLocation());
    }

    @Test
    @JvmSetting(key = JvmSettings.OIDC_AUTH_SERVER_URL, value = "https://auth.example.org/not-keycloak")
    void keycloakLocationIsEmptyForNonKeycloakOidcSetting() {
        assertTrue(PlaticaNavbarUtil.getKeycloakLocation().isEmpty());
    }

    @Test
    void parseAuthServerUrlHandlesTrailingSlashAndContextPath() {
        assertEquals(Optional.of(new KeycloakLocation("https://auth.example.org", "platica")),
                PlaticaNavbarUtil.parseAuthServerUrl("https://auth.example.org/realms/platica/"));
        assertEquals(Optional.of(new KeycloakLocation("https://auth.example.org/auth", "test")),
                PlaticaNavbarUtil.parseAuthServerUrl("https://auth.example.org/auth/realms/test"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://auth.example.org", "https://auth.example.org/realms/",
            "/realms/platica", "https://auth.example.org/realms/platica/protocol"})
    void parseAuthServerUrlRejectsNonKeycloakUrls(String url) {
        assertTrue(PlaticaNavbarUtil.parseAuthServerUrl(url).isEmpty());
    }

    @Test
    void parseAuthServerUrlRejectsNull() {
        assertTrue(PlaticaNavbarUtil.parseAuthServerUrl(null).isEmpty());
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_LOCALE_COOKIE_NAME, value = "PLATICA_lang")
    @JvmSetting(key = JvmSettings.UI_LOCALE_COOKIE_DOMAIN, value = ".rediris.es")
    void localeCookieSettingsAreRead() {
        assertEquals("PLATICA_lang", PlaticaNavbarUtil.getLocaleCookieName());
        assertEquals(Optional.of(".rediris.es"), PlaticaNavbarUtil.getLocaleCookieDomain());
    }

    @Test
    void menuItemsJsonUsesWidgetFormat() {
        String json = PlaticaNavbarUtil.toMenuItemsJson(List.of(
                new MenuItem("profile", "View profile", "eye", "https://web.example.org/es/mi-cuenta", true),
                new MenuItem("my-data", "My \"Data\"", "document-text", "/dataverseuser.xhtml?selectTab=dataRelatedToMe", false)));

        JsonArray items = JsonUtil.getJsonArray(json);
        assertEquals(2, items.size());

        JsonObject profile = items.getJsonObject(0);
        assertEquals("profile", profile.getString("id"));
        assertEquals("View profile", profile.getString("label"));
        assertEquals("eye", profile.getString("icon"));
        assertEquals("https://web.example.org/es/mi-cuenta", profile.getString("url"));
        assertTrue(profile.getBoolean("isExternal"));
        assertEquals("custom", profile.getString("group"));

        JsonObject myData = items.getJsonObject(1);
        // Quotes in labels must survive the round trip (escaped JSON, not broken attribute)
        assertEquals("My \"Data\"", myData.getString("label"));
        assertFalse(myData.getBoolean("isExternal"));
    }

    @Test
    void menuItemsJsonIsEmptyArrayWithoutItems() {
        assertEquals("[]", PlaticaNavbarUtil.toMenuItemsJson(List.of()));
    }

    @Test
    void languagesJsonKeepsOrderAndNormalizesCase() {
        Map<String, String> locales = new LinkedHashMap<>();
        locales.put("es", "Español");
        locales.put("EN", "English");
        locales.put("gl", "Galego");

        JsonArray languages = JsonUtil.getJsonArray(PlaticaNavbarUtil.toLanguagesJson(locales));
        assertEquals(3, languages.size());
        assertEquals("ES", languages.getJsonObject(0).getString("label"));
        assertEquals("es", languages.getJsonObject(0).getString("value"));
        assertEquals("EN", languages.getJsonObject(1).getString("label"));
        assertEquals("en", languages.getJsonObject(1).getString("value"));
        assertEquals("gl", languages.getJsonObject(2).getString("value"));
    }

    @Test
    @JvmSetting(key = JvmSettings.UI_LOCALE_COOKIE_NAME, value = "  ")
    void blankLocaleCookieNameFallsBackToDefault() {
        assertEquals(PlaticaNavbarUtil.DEFAULT_LOCALE_COOKIE_NAME, PlaticaNavbarUtil.getLocaleCookieName());
    }
}
