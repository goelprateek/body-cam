FROM gradle:9.1.0-jdk21 AS build

WORKDIR /app

COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
COPY gradlew .
COPY gradle ./gradle

# Fix permission issue on Linux container
RUN chmod +x gradlew

COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home spring \
    && mkdir -p /app/logs/archive

COPY --from=build /app/build/libs/bodycam-backend-0.0.1-SNAPSHOT.jar app.jar

RUN chown -R spring:spring /app

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Djava.security.egd=file:/dev/urandom", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=20.0", "-XX:+UseStringDeduplication", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
