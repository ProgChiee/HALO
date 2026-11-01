
# ==========================================
# HALO - Spring Boot Backend
# Java 17 + Maven + Aiven MySQL SSL
# ==========================================

# STAGE 1: Build Spring Boot application
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package


# STAGE 2: Run Spring Boot application
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Create startup script for Aiven SSL
RUN printf '%s\n' \
    '#!/bin/sh' \
    'set -eu' \
    '' \
    '# Require Aiven CA certificate' \
    'if [ -z "${AIVEN_CA_CERT:-}" ]; then' \
    '  echo "ERROR: AIVEN_CA_CERT is required" >&2' \
    '  exit 1' \
    'fi' \
    '' \
    '# Create certificate file' \
    'printf "%s\n" "$AIVEN_CA_CERT" > /tmp/aiven-ca.pem' \
    '' \
    '# Create Java truststore' \
    'keytool -importcert -noprompt -trustcacerts -alias aiven-ca -file /tmp/aiven-ca.pem -keystore /tmp/aiven-truststore.p12 -storetype PKCS12 -storepass changeit' \
    '' \
    '# Remove temporary certificate file' \
    'rm -f /tmp/aiven-ca.pem' \
    '' \
    '# Configure Java SSL truststore' \
    'export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/tmp/aiven-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"' \
    '' \
    '# Start Spring Boot' \
    'exec java -jar /app/app.jar' \
    > /app/start.sh \
    && chmod +x /app/start.sh

EXPOSE 8081

ENTRYPOINT ["/app/start.sh"]
