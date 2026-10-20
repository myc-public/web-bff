# web-bff

BFF Spring Boot partage par les fronts web : session serveur (Redis), OIDC, tokens jamais dans le navigateur.
Lire d'abord [README.md](README.md). Regles communes du socle : `platform-handbook`.

## Commandes

- Tests : `mvn test` (sans Docker, profil `test`, sessions en memoire).
- Mode dev : Redis par `docker compose -f local/compose.yaml --env-file local/.env up -d`, puis `mvn spring-boot:run`
  (port 8090) ; Keycloak local et API demarres.
- Tests SEC-BFF reels : `gitops-platform/tools/tests/sec-bff.ps1` (pile ou Sandbox).

## Regles propres a ce depot

- Aucun token (access, refresh, id) ne sort du BFF : ni reponse, ni cookie lisible, ni log.
- Routes vers l'API en liste fermee : toute nouvelle route modifie `RoutesGuardTest` ; jamais de proxy generique.
- Toute mutation exige l'origine autorisee (`BFF_ALLOWED_ORIGINS`) et le jeton CSRF.
- Configuration validee au demarrage (`BffProperties`) : une erreur nomme la propriete, jamais sa valeur ;
  nouvelle variable = ajout dans `local/.env.example` et dans le tableau du README.
- `local/.env` n'est jamais ecrase ni affiche.
- Decisions DB1-DB11 (ADR 0026, 0027 de `platform-handbook`) : tout ecart passe par une nouvelle ADR.
