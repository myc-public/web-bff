# web-bff

BFF (Backend For Frontend) partage par les fronts web, en Spring Boot 4 / Spring Security 7 (lot K6). Il porte la
connexion OIDC et la session ; les tokens restent cote serveur, dans Redis, et le navigateur ne recoit qu'un cookie
opaque `__Host-SESSION`. Une image, une instance par realm : `web-bff-customers` (fronts donateurs, `myc-customers`),
`web-bff-internal` (back-office, `myc-internal`, K6b).

Flux : Navigateur -> APISIX externe + WAF -> `/bff/**` -> **web-bff** -> APISIX interne -> donation-api ; connexion
par Keycloak (client `web-bff`, code + PKCE S256). Decisions : ADR K6 DB1 a DB11 et resultats du spike du 13/10
(`jenkins-on-openshift-with-terraform/docs/ROADMAP.md`). Tests de securite : `inner-donation-api/postman/PLAN-DE-TEST-E2E.md`
sections 5.9 et 5.10.

## Contrat avec les fronts

| Route (sur l'origine du front) | Role |
|---|---|
| `GET /bff/oauth2/authorization/keycloak` | connexion (navigation pleine page), retour sur `/` |
| `GET /bff/api/session` | `{authenticated, givenName, familyName, email}` ; jamais de token |
| `GET /bff/api/donors/me` | profil (4 champs) ; 404 = pas encore de profil |
| `POST /bff/api/donors/me` | creation du profil ; 201, 409 = deja cree |
| `POST /bff/logout` | deconnexion (formulaire avec champ `_csrf`), puis Keycloak, retour sur `/` |

Mutations : en-tete `Origin` d'un front autorise (`BFF_ALLOWED_ORIGINS`) et jeton CSRF (cookie `XSRF-TOKEN` renvoye
dans l'en-tete `X-XSRF-TOKEN` ou le champ `_csrf`). Sans session : 401 sur `/bff/api/**` (jamais de redirection).
Erreurs : 401 = reconnexion, 503 = API injoignable, 502 = erreur de l'API. Ajouter une route = modifier
`RoutesGuardTest` (liste fermee, jamais de proxy generique).

Jamais routes par APISIX externe : back-channel logout (`/bff/logout/connect/back-channel/keycloak`, URL interne
appelee par Keycloak) et sondes (`/bff/management/health/liveness`, `/bff/management/health/readiness`).

## Prerequis (mode dev)

- Java 21, Maven ; parent `craft-parent` depuis Nexus.
- Keycloak local demarre (`gitops-platform/local/keycloak`), realm a jour (client `web-bff`).
- API en mode dev (`inner-donation-api`, port 8080).

## Mode dev (BFF sur l'hote, port 8090)

```powershell
cd D:\workspace\public\web-bff
# Une seule fois (ne jamais ecraser un fichier existant), puis renseigner OIDC_CLIENT_SECRET et REDIS_PASSWORD
if (-not (Test-Path local\.env)) { Copy-Item local\.env.example local\.env }
docker compose -f local/compose.yaml --env-file local/.env up -d     # Redis des sessions (ACL de l'instance)
mvn spring-boot:run                                                  # http://localhost:8090/bff
```

Le front (Vite, port 5173) envoie `/bff` vers ce BFF : l'origine vue par le navigateur et par Keycloak est
`http://localhost:5173`. Keycloak (Docker) appelle le back-channel sur `http://host.docker.internal:8090`.

## Configuration

Variables d'environnement validees au demarrage (`BffProperties`) : une valeur absente ou invalide arrete le BFF avec
le nom de la propriete, jamais sa valeur. Liste et valeurs factices : `local/.env.example`.

| Variable | Role |
|---|---|
| `BFF_INSTANCE` | nom de l'instance : prefixe des cles Redis et utilisateur ACL Redis |
| `OIDC_ISSUER_URI` / `OIDC_INTERNAL_URI` | emetteur public (iss verifie, endpoints du navigateur) / URL interne de Keycloak |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | client confidentiel du realm |
| `BFF_ALLOWED_ORIGINS` | origines des fronts autorisees pour les mutations |
| `API_BASE_URL` | APISIX interne (dev : l'API) |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis partage, mot de passe de l'utilisateur de l'instance |
| `SESSION_IDLE_TIMEOUT` / `SESSION_ABSOLUTE_TIMEOUT` | 30 min d'inactivite, 10 h au maximum |
| `OTLP_EXPORT_ENABLED` / `OTLP_ENDPOINT` | traces, metriques et logs OTLP (desactives par defaut) |

## Tests

`mvn test` : sans Docker, sans Keycloak ni Redis (profil `test` : sessions en memoire). Couvre la liste fermee des
routes, PKCE S256, le cookie `__Host-SESSION`, l'origine du front, Origin + CSRF sur toutes les mutations, la
session sans token, les erreurs (401, 404, 409, 502, 503), la deconnexion, le back-channel (hors CSRF), le verrou de
refresh (refresh unique, attente et relecture, delai, erreur reseau), le registre `sid` (suppression ciblee, rejeu
`jti`) et la validation de la configuration. Redis, Keycloak et plusieurs replicas reels : mode pile (SEC-BFF).
