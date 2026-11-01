
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
    '# Create temporary certificate file' \
    'printf "%s\n" "$AIVEN_CA_CERT" > /tmp/aiven-ca.pem' \
    '' \
    '# Copy default Java truststore' \
    'cp "$JAVA_HOME/lib/security/cacerts" /tmp/halo-truststore' \
    '' \
    '# Import Aiven CA into Java truststore' \
    'keytool -importcert -noprompt -trustcacerts -alias aiven-ca -file /tmp/aiven-ca.pem -keystore /tmp/halo-truststore -storepass changeit' \
    '' \
    '# Remove temporary certificate file' \
    'rm -f /tmp/aiven-ca.pem' \
    '' \
    '# Configure Java SSL truststore' \
    'export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=/tmp/halo-truststore -Djavax.net.ssl.trustStorePassword=changeit"' \
    '' \
    '# Start Spring Boot application' \
    'exec java -jar /app/app.jar' \
    > /app/start.sh \
    && chmod +x /app/start.sh

EXPOSE 8081

ENTRYPOINT ["/app/start.sh"]
