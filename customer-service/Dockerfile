# Runtime image for customer-service
# Expects the JAR to be built and available in the build context
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy all JARs first, then select the executable one
# Spring Boot Maven plugin creates two JARs:
# - customer-service-1.0.0-SNAPSHOT.jar (executable/fat JAR - this is what we want)
# - customer-service-1.0.0-SNAPSHOT-plain.jar (regular JAR - not executable)
COPY target/*.jar /tmp/

# Select the executable JAR (without -plain suffix) and move it to app.jar
RUN find /tmp -name "customer-service-*.jar" ! -name "*-plain.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" -exec mv {} app.jar \; && \
    if [ ! -f app.jar ]; then \
        echo "Error: Executable JAR not found. Available JARs:"; \
        ls -la /tmp/*.jar; \
        exit 1; \
    fi && \
    rm -f /tmp/*.jar

# Expose the application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]




