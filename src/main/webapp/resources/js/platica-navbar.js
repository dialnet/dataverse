/*
 * PLATICA common header widget (<navbar-root>) integration for the JSF UI.
 *
 * The widget replaces the right section of the navbar (language selector, login and account menu).
 * Its configuration comes from data-* attributes rendered in dataverse_header.xhtml. This script:
 *  - loads the widget (an ES module served by PLATICA web),
 *  - gives it a Keycloak access token for the logged-in user, obtained silently with keycloak-js
 *    (check-sso), and keeps it fresh, so the widget can load the profile, notifications and
 *    management links from core-admon,
 *  - reacts to the widget's events: language change (shared language cookie + reload), sign in and
 *    sign out (Dataverse's own login page and OIDC logout).
 *
 * Mirrors the OpenCDMP integration (frontend/src/app/ui/navbar/navbar.component.ts).
 */
(function () {
    'use strict';

    // Same keycloak-js version OpenCDMP uses against the same Keycloak server
    var KEYCLOAK_JS_URL = 'https://cdn.jsdelivr.net/npm/keycloak-js@25.0.6/dist/keycloak.min.js';
    var KEYCLOAK_JS_INTEGRITY = 'sha384-NmCPv4jhNJcDEObOO341rI8/t+bKz5bBLSr3AcJ2806Nd8LRqaoE3wQzB8DLtVbh';
    var LOCALE_COOKIE_MAX_AGE = 31536000; // 1 year, as DataverseSession.setLocaleCode
    var TOKEN_MIN_VALIDITY_SECONDS = 30;

    function warn(message, detail) {
        if (window.console && console.warn) {
            console.warn('[platica-navbar] ' + message, detail === undefined ? '' : detail);
        }
    }

    function loadWidget(scriptUrl) {
        if (document.querySelector('script[data-platica-navbar]')) {
            return;
        }
        var script = document.createElement('script');
        script.type = 'module';
        script.src = scriptUrl;
        script.setAttribute('data-platica-navbar', '');
        script.onerror = function () {
            warn('could not load the widget from', scriptUrl);
        };
        document.head.appendChild(script);
    }

    function loadKeycloakJs() {
        if (window.Keycloak) {
            return Promise.resolve(window.Keycloak);
        }
        return new Promise(function (resolve, reject) {
            // The UMD build registers as an AMD module instead of a global when an AMD loader is present
            var amd = (typeof window.define === 'function') ? window.define.amd : undefined;
            if (amd) {
                window.define.amd = undefined;
            }
            var restoreAmd = function () {
                if (amd) {
                    window.define.amd = amd;
                }
            };
            var script = document.createElement('script');
            script.src = KEYCLOAK_JS_URL;
            script.integrity = KEYCLOAK_JS_INTEGRITY;
            script.crossOrigin = 'anonymous';
            script.onload = function () {
                restoreAmd();
                if (window.Keycloak) {
                    resolve(window.Keycloak);
                } else {
                    reject(new Error('keycloak-js loaded but Keycloak is not defined'));
                }
            };
            script.onerror = function () {
                restoreAmd();
                reject(new Error('could not load keycloak-js'));
            };
            document.head.appendChild(script);
        });
    }

    /*
     * Silently reuses the user's Keycloak session (the same one used to log in to Dataverse) to get an
     * access token for the widget, and refreshes it before it expires. Resolves with no token if there is
     * no Keycloak session or the browser can't talk to Keycloak (e.g. missing Web Origins in the client).
     */
    function connectAccessToken(widget, config) {
        loadKeycloakJs().then(function (Keycloak) {
            var keycloak = new Keycloak({
                url: config.keycloakUrl,
                realm: config.keycloakRealm,
                clientId: config.keycloakClientId
            });
            var setToken = function () {
                widget.accessToken = keycloak.token || undefined;
            };
            keycloak.onAuthSuccess = setToken;
            keycloak.onAuthRefreshSuccess = setToken;
            keycloak.onAuthRefreshError = function () {
                widget.accessToken = undefined;
            };
            keycloak.onTokenExpired = function () {
                keycloak.updateToken(TOKEN_MIN_VALIDITY_SECONDS).catch(function (error) {
                    warn('could not refresh the access token', error);
                    widget.accessToken = undefined;
                });
            };
            return keycloak.init({
                onLoad: 'check-sso',
                silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
                pkceMethod: 'S256',
                checkLoginIframe: false
            }).then(function (authenticated) {
                if (authenticated) {
                    setToken();
                } else {
                    warn('no Keycloak session found; the widget will show the guest state');
                }
            });
        }).catch(function (error) {
            warn('could not get an access token for the widget', error);
        });
    }

    function parseLanguageCodes(widget) {
        try {
            return JSON.parse(widget.getAttribute('languages') || '[]').map(function (language) {
                return String(language.value).toLowerCase();
            });
        } catch (e) {
            return [];
        }
    }

    // Browsers silently drop a cookie whose Domain doesn't match the page's host (e.g. Domain=rediris.es on
    // localhost), so the configured domain is only used when the page is actually served under it.
    function cookieDomainFor(hostname, configuredDomain) {
        var domain = (configuredDomain || '').replace(/^\./, '').toLowerCase();
        var host = hostname.toLowerCase();
        if (domain && (host === domain || host.slice(-(domain.length + 1)) === '.' + domain)) {
            return domain;
        }
        return null;
    }

    // Same cookie Dataverse reads (DataverseSession.getLocaleCode) and PLATICA web / OpenCDMP share
    function writeLocaleCookie(config, code) {
        var cookie = encodeURIComponent(config.cookieName) + '=' + encodeURIComponent(code)
            + '; Path=/; Max-Age=' + LOCALE_COOKIE_MAX_AGE + '; SameSite=Lax';
        var domain = cookieDomainFor(window.location.hostname, config.cookieDomain);
        if (domain) {
            cookie += '; Domain=' + domain;
        } else if (config.cookieDomain) {
            warn('cookie domain ' + config.cookieDomain + ' does not match ' + window.location.hostname
                + '; writing a host-only cookie');
        }
        if (window.location.protocol === 'https:') {
            cookie += '; Secure';
        }
        document.cookie = cookie;
    }

    function onLanguageChange(widget, config, event) {
        var code = String(event.detail || '').toLowerCase();
        if (!code || parseLanguageCodes(widget).indexOf(code) === -1) {
            warn('ignoring unsupported language', event.detail);
            return;
        }
        if (code === (widget.getAttribute('language') || '').toLowerCase()) {
            return;
        }
        writeLocaleCookie(config, code);
        window.location.reload();
    }

    function onSignOut() {
        var logout = document.querySelector('[id$="lnk_platica_logout"]');
        if (logout) {
            logout.click();
        } else {
            warn('logout link not found');
        }
    }

    function readConfig(widget) {
        var data = widget.dataset;
        return {
            scriptUrl: data.scriptUrl,
            authenticated: data.authenticated === 'true',
            loginUrl: data.loginUrl || '/loginpage.xhtml',
            keycloakUrl: data.keycloakUrl,
            keycloakRealm: data.keycloakRealm,
            keycloakClientId: data.keycloakClientId,
            cookieName: data.cookieName,
            cookieDomain: data.cookieDomain
        };
    }

    function init() {
        var widget = document.getElementById('platica-navbar');
        if (!widget) {
            return;
        }
        var config = readConfig(widget);
        if (!config.scriptUrl) {
            return;
        }

        widget.addEventListener('navbarLanguageChange', function (event) {
            onLanguageChange(widget, config, event);
        });
        widget.addEventListener('signInRequested', function () {
            window.location.href = config.loginUrl;
        });
        widget.addEventListener('signOutRequested', onSignOut);
        // Menu items are real links that navigate by themselves; nothing to do on menuItemClick.
        widget.addEventListener('platformBackendError', function (event) {
            warn('backend error', event.detail);
        });

        loadWidget(config.scriptUrl);

        // Properties must be set on the upgraded element, not on the plain placeholder
        customElements.whenDefined('navbar-root').then(function () {
            widget.accessToken = undefined;
            if (!config.authenticated) {
                return;
            }
            if (config.keycloakUrl && config.keycloakRealm && config.keycloakClientId) {
                connectAccessToken(widget, config);
            } else {
                warn('Keycloak is not configured (dataverse.ui.platica.keycloak-client-id / dataverse.auth.oidc.auth-server-url);'
                    + ' the widget will show the guest state');
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
}());
