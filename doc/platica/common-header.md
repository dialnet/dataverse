# Cabecera común PLATICA en Dataverse

Dataverse (interfaz JSF) integra el widget de cabecera común de PLATICA, `<navbar-root>`
(proyecto `platica-navbar`), el mismo que usa OpenCDMP/PGD. El widget sustituye la **parte derecha**
de la barra de navegación de Dataverse: selector de idioma, inicio de sesión y menú de cuenta. Muestra
además la campana de notificaciones y los enlaces de gestión de la plataforma.

La parte izquierda (logo) y los enlaces centrales ("Añadir datos", "Buscar", "Guía de usuario",
"Soporte") siguen siendo de Dataverse.

Si no se configura la URL del widget, Dataverse muestra su cabecera original: la integración está
desactivada por defecto.

## Cómo funciona

1. **Carga del widget.** `platica-navbar.js` carga el módulo del widget desde el servidor de ficheros
   estáticos de PLATICA (SAS), por ejemplo
   `https://platica-web-pre.rediris.es/sas/core-navbar/v1.0.0/shared-navbar.esm.js`.
2. **Inicio de sesión.** Sigue siendo el de Dataverse (OIDC en el servidor, con el cliente confidencial
   de Keycloak). El botón "Iniciar sesión" del widget lleva a `/loginpage.xhtml`, que redirige a Keycloak/SIR2.
3. **Token para el widget.** Dataverse no conserva el token de acceso en el navegador. Para el usuario
   que ha iniciado sesión, `platica-navbar.js` obtiene uno con `keycloak-js` 25.0.6 (`check-sso`
   silencioso con PKCE `S256`, usando la sesión de Keycloak ya abierta) y con el **cliente público** de
   Keycloak. Lo renueva antes de que caduque y se lo pasa al widget.
4. **core-admon.** Con ese token, el widget pide a core-admon el perfil, los roles, los enlaces de
   gestión (`/api/session`) y el resumen de notificaciones de cada aplicación (`/api/notifications/summary`).
5. **Idioma.** Dataverse indica al widget el idioma actual (atributo `language`). Al cambiarlo en el
   widget, `platica-navbar.js` escribe la cookie compartida `PLATICA_lang` y recarga la página. La misma
   cookie la leen y escriben PLATICA web y OpenCDMP.
6. **Cierre de sesión.** El widget lo solicita y `platica-navbar.js` ejecuta el cierre de sesión de
   Dataverse, que también cierra la sesión de Keycloak.
7. **Alternativa de cierre de sesión.** Si el widget no consigue la sesión de un usuario que sí ha
   iniciado sesión en Dataverse, muestra "Iniciar sesión". En ese caso aparece junto al widget el enlace
   "Cerrar sesión" de Dataverse, y la consola indica el motivo.

El menú de perfil del widget incluye: **Ver perfil** (PLATICA web), **Mis datos** y, para
superusuarios, **Panel de control**.

## Configuración de Dataverse

### Opciones JVM / MicroProfile (nuevas)

| Opción | Variable de entorno | Descripción |
|---|---|---|
| `dataverse.ui.platica.navbar-script-url` | `DATAVERSE_UI_PLATICA_NAVBAR_SCRIPT_URL` | URL del módulo del widget. **Vacía = cabecera original de Dataverse.** Sin valor por defecto. |
| `dataverse.ui.platica.keycloak-client-id` | `DATAVERSE_UI_PLATICA_KEYCLOAK_CLIENT_ID` | Cliente **público** de Keycloak para el token del widget (en pre: `dataverse-client-public`). Sin él, el widget queda como invitado. |
| `dataverse.ui.platica.profile-url` | `DATAVERSE_UI_PLATICA_PROFILE_URL` | Enlace "Ver perfil". Por defecto `https://platica-web-pre.rediris.es/es/mi-cuenta`. |

La URL de Keycloak y el realm **no se configuran aparte**: se obtienen de
`dataverse.auth.oidc.auth-server-url` (p. ej. `https://platica-auth-pre.rediris.es/realms/platica`).

### Opciones existentes que intervienen

| Opción | Uso |
|---|---|
| `dataverse.ui.locale-cookie-name` / `DATAVERSE_UI_LOCALE_COOKIE_NAME` | Nombre de la cookie de idioma: **`PLATICA_lang`**. |
| `dataverse.ui.locale-cookie-domain` / `DATAVERSE_UI_LOCALE_COOKIE_DOMAIN` | Dominio compartido de la cookie (en pre: `rediris.es`). El script solo lo usa si la página está bajo ese dominio. |
| `:Languages` (setting de BD) | Idiomas que ofrece el widget, en el mismo orden. |
| `:NavbarGuidesUrl`, `:NavbarSupportUrl` (settings de BD) | Enlaces "Guía de usuario" y "Soporte". |
| `DATAVERSE_FEATURE_API_BEARER_AUTH` | Necesario para que core-admon pueda consultar las notificaciones de Dataverse con el token del usuario. |
| `-Ddataverse.lang.directory` | Carpeta de traducciones. Incluir la clave nueva `header.platica.profile` (es/gl: "Ver perfil", eu: "Ikusi profila", ca: "Veure el perfil"). |

## Lista de comprobación por entorno (PRE, STAGING, PROD)

Cada entorno usa sus propias URL. `<dataverse>` es la URL pública de Dataverse en ese entorno
(en pre, `https://platica-repo-pre.rediris.es`).

**Keycloak (realm de PLATICA)**
- Cliente **público** (sin secreto), flujo estándar y PKCE `S256` (en pre: `dataverse-client-public`).
  - *Valid redirect URIs*: `<dataverse>/*`
  - *Web origins*: `<dataverse>`
  - No sirve el cliente confidencial de Dataverse (`dataverse-client`): su secreto no puede ir al navegador.
- *Realm settings → Security defenses → Headers → Content-Security-Policy*: añadir `<dataverse>` a `frame-ancestors` (lo necesita el `check-sso` silencioso).

**core-admon**
- CORS: permitir el origen `<dataverse>` (v1.0.0 llama a core-admon desde el navegador).
- Aceptar los tokens emitidos para el cliente público de Dataverse.
- Fuente de notificaciones de Dataverse en `/api/notifications/summary`:
  - `appCode`: `repository` (el widget ya tiene icono para ella)
  - recuento: `GET <dataverse>/api/notifications/unreadCount` con `Authorization: Bearer <token del usuario>` → `{"status":"OK","data":{"unreadCount":N}}`
  - `destinationUrl`: `<dataverse>/dataverseuser.xhtml?selectTab=notifications`

**Dataverse**
- Opciones JVM: `navbar-script-url` (versión del widget publicada en ese entorno), `keycloak-client-id`, `profile-url` (PLATICA web de ese entorno).
- Cookie de idioma: `PLATICA_lang` y el dominio compartido del entorno.
- Settings de BD: `:NavbarGuidesUrl`, `:NavbarSupportUrl` y `:Languages`.
- Traducciones de `header.platica.profile` en los ficheros de idioma.

**Otras aplicaciones**
- PLATICA web y OpenCDMP escriben `PLATICA_lang` en el mismo dominio compartido.

## PRE

Valores de pre:

```bash
# Settings de base de datos (afectan a la BD compartida de pre; los ejecuta quien administra pre)
curl -X PUT -d 'https://platica-web-pre.rediris.es/es/documentacion/' http://localhost:8080/api/admin/settings/:NavbarGuidesUrl
curl -X PUT -d 'https://platica-web-pre.rediris.es/es/contacto/'      http://localhost:8080/api/admin/settings/:NavbarSupportUrl
```

```yaml
# Entorno del contenedor de Dataverse
DATAVERSE_UI_PLATICA_NAVBAR_SCRIPT_URL: "https://platica-web-pre.rediris.es/sas/core-navbar/v1.0.0/shared-navbar.esm.js"
DATAVERSE_UI_PLATICA_KEYCLOAK_CLIENT_ID: "dataverse-client-public"
DATAVERSE_UI_LOCALE_COOKIE_NAME: "PLATICA_lang"
DATAVERSE_UI_LOCALE_COOKIE_DOMAIN: "rediris.es"
```

Estado en pre (2026-11-11):
- !Ojo! Dataverse pre está en la versión **6.10.1**; esta rama parte de una versión posterior de `develop-dialnet`, así que al desplegarla Flyway aplicará migraciones nuevas en la BD de pre.

## Versión del widget

Se usa **v1.0.0**, la misma que OpenCDMP y la única publicada en pre (el alias `latest` del SAS es una
compilación anterior a v1.0.0 y no debe usarse). Para comprobar si una versión está publicada:

### Paso a v1.0.2

En Dataverse es solo un cambio de configuración (`navbar-script-url`), pero requiere antes:
1. **Publicar v1.0.2 en el SAS** de cada entorno.
2. **Enrutar `/core-admon-api` en el dominio de Dataverse.** Desde v1.0.1 el widget llama a core-admon
   con una ruta relativa (`<dataverse>/core-admon-api/...`) en lugar de la dirección absoluta de v1.0.0.
3. **Cookie de idioma.** v1.0.2 escribe su propia `PLATICA_lang`, con dominio compartido solo bajo
   `stage.platica.gob.es` y `platica.gob.es`; en cualquier otro host (p. ej. `*.rediris.es`) la escribe
   solo para ese host. En pre convivirían dos cookies `PLATICA_lang` y el idioma podría cambiar de forma
   imprevisible. Bajo `platica.gob.es` no hay problema.
4. Cambiar OpenCDMP y Dataverse a la vez, para que la cabecera se comporte igual en ambas.

## Desarrollo local

- Activar el widget en `docker-compose-dev.yml` (fichero local, no se commitea) con las mismas variables
  de entorno que en pre.
- **core-admon no permite CORS desde `http://localhost:8080`**: en local el widget se queda como
  invitado (y aparece el enlace alternativo de cierre de sesión). Para probar el estado con sesión,
  usar una ventana de Chrome desechable sin seguridad web, **solo para localhost**, y cerrarla al terminar:
  ```bash
  google-chrome --user-data-dir=/tmp/chrome-cors-test --disable-web-security http://localhost:8080
  ```
- Si Docker no llega al túnel de Solr del host y `ufw` está activo:
  `sudo ufw allow from 172.16.0.0/12 to any port 18983 proto tcp`.
- Traducciones en local: `-Ddataverse.lang.directory=/dv/lang` en `JVM_ARGS` y los ficheros en
  `docker-dev-volumes/app/data/lang` (traducciones + ficheros base actuales de `src/main/java/propertyFiles`).

## Solución de problemas

Los mensajes de la integración aparecen en la consola del navegador con el prefijo `[platica-navbar]`.

| Síntoma / mensaje | Causa probable |
|---|---|
| Cabecera original de Dataverse, sin widget | `navbar-script-url` vacía. |
| `could not load the widget from …` | Versión no publicada en el SAS o URL incorrecta. |
| `Framing 'https://<keycloak>/' violates … frame-ancestors` | Falta `<dataverse>` en la CSP del realm. |
| `Timeout when waiting for 3rd party check iframe message` | Igual que la anterior: el iframe de `check-sso` está bloqueado. |
| CORS en `…/openid-connect/token` (respuesta 200 sin `Access-Control-Allow-Origin`) | Falta `<dataverse>` en *Web origins* del cliente público. |
| `could not get an access token for the widget` | Cualquiera de las tres anteriores, o cliente no configurado. |
| `backend error {operation: 'resolveSession' …}` y CORS en `core-admon-api` | core-admon no permite el origen de Dataverse (normal en `localhost`). |
| El idioma no cambia en `localhost` | Dominio de la cookie distinto del host: el script escribe entonces una cookie solo para el host y lo avisa en consola. |
| La campana no muestra Dataverse | core-admon no devuelve la fuente `repository` (ver la lista de comprobación). |

Mensajes inofensivos: `unload is not allowed` (jQuery de PrimeFaces), aviso de `sandbox` con
`allow-scripts` y `allow-same-origin` (iframe de `keycloak-js`) y `requestStorageAccess` en ventanas
de incógnito.

## Código

| Fichero | Contenido |
|---|---|
| `src/main/java/edu/harvard/iq/dataverse/settings/JvmSettings.java` | Opciones `dataverse.ui.platica.*` |
| `src/main/java/edu/harvard/iq/dataverse/util/PlaticaNavbarUtil.java` | Lectura de opciones, URL y realm de Keycloak, JSON de idiomas y menú |
| `src/main/java/edu/harvard/iq/dataverse/SettingsWrapper.java`, `DataverseHeaderFragment.java` | Acceso desde JSF; menú de perfil |
| `src/main/webapp/dataverse_header.xhtml` | `<navbar-root>`, configuración `data-*` y formulario de cierre de sesión |
| `src/main/webapp/resources/js/platica-navbar.js` | Carga del widget, token, idioma, inicio y cierre de sesión |
| `src/main/webapp/silent-check-sso.html` | Página del `check-sso` silencioso |
| `src/test/java/edu/harvard/iq/dataverse/util/PlaticaNavbarUtilTest.java` | Tests unitarios |
