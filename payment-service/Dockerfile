# payment-service — production image (built from the JAR produced by: .\mvnw.cmd -DskipTests package)
#
# Stage 1 splits the Spring Boot fat JAR into layers (dependencies change
# rarely, application code changes often) so a code change only rebuilds and
# re-pushes the small top layer instead of ~80 MB of libraries.
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /build
COPY target/payment-service-0.0.1-SNAPSHOT.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
# Never run as root inside the container.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
USER app
# Checked by docker-entrypoint.sh before the JVM starts.
ENV REQUIRED_ENV="DB_URL DB_USERNAME DB_PASSWORD EUREKA_URL INTERNAL_API_KEY JWT_STAFF_PUBLIC_KEY_PATH JWT_CUSTOMER_PUBLIC_KEY_PATH RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY S3_ENDPOINT S3_PUBLIC_ENDPOINT"
# Heap sized from the CONTAINER memory limit, not the host's RAM; crash fast on
# OOM so the platform restarts a clean instance instead of limping along.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"     SPRING_PROFILES_ACTIVE=prod
EXPOSE 8084
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5   CMD wget -qO- http://127.0.0.1:8084/actuator/health/liveness || exit 1
ENTRYPOINT ["docker-entrypoint.sh"]
