# web-bff : une image, une instance par realm (DB2) ; toute la configuration vient de l'environnement.
# CI : le BuildConfig impose l'image de base (dockerStrategy.from -> openshift/ubi8-openjdk-21-runtime:1.18)
#      et le contexte ne contient que app.jar (valeurs par defaut ci-dessous).
# Local (pile) : gitops-platform/local/stack/web-bff.pile.yaml passe JAR_FILE=target/*.jar (apres mvn package).
ARG BASE_IMAGE=registry.access.redhat.com/ubi8/openjdk-21-runtime:1.18
FROM ${BASE_IMAGE}

ARG JAR_FILE=app.jar
COPY ${JAR_FILE} /deployments/app.jar

EXPOSE 8080
USER 185

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/deployments/app.jar"]
