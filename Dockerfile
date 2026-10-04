# syntax=docker/dockerfile:1

FROM maven:3.9.16-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src/main/ src/main/
RUN mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package \
    && cp target/ledgerbank-*.jar /tmp/ledgerbank.jar

FROM eclipse-temurin:25-jre-noble AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system ledgerbank \
    && useradd --system --gid ledgerbank --home-dir /app --shell /usr/sbin/nologin ledgerbank

WORKDIR /app

COPY --from=build --chown=ledgerbank:ledgerbank /tmp/ledgerbank.jar app.jar

USER ledgerbank

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
