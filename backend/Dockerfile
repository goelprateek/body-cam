FROM gradle:9.1.0-jdk25 AS build

WORKDIR /app

COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .
COPY gradlew .
COPY gradle ./gradle
COPY src ./src

RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jdk AS runtime-build

WORKDIR /work
COPY --from=build /app/build/libs/bodycam-backend-0.0.1-SNAPSHOT.jar app.jar

# Build a minimized Java runtime containing only the modules this app needs.
RUN mkdir extracted \
    && cd extracted \
    && jar xf ../app.jar \
    && modules="$($JAVA_HOME/bin/jdeps --ignore-missing-deps --multi-release 25 --recursive --print-module-deps BOOT-INF/lib/*.jar BOOT-INF/classes)" \
    && $JAVA_HOME/bin/jlink \
      --add-modules "$modules" \
      --strip-debug \
      --no-header-files \
      --no-man-pages \
      --compress=2 \
      --output /work/custom-jre

FROM debian:bookworm-slim

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home spring

COPY --from=runtime-build /work/custom-jre ${JAVA_HOME}
COPY --from=build /app/build/libs/bodycam-backend-0.0.1-SNAPSHOT.jar app.jar

RUN chown -R spring:spring /app ${JAVA_HOME}

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Djava.security.egd=file:/dev/urandom", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-XX:InitialRAMPercentage=20.0", "-XX:+UseStringDeduplication", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
