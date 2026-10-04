# Development and Automation

## Prerequisites

- JDK 25 available through **java** and **JAVA_HOME**.
- Docker Engine or Docker Desktop with Docker Compose v2 (**docker compose**).
- Bash for repository scripts.
- **curl** for the application health check.
- No system Maven installation is required; use the committed Maven Wrapper.

The scripts resolve the repository root from their own location, so they can be invoked from any working directory inside or outside the repository.

## First local run

The local profile and Compose file have development-only defaults. Copy **.env.example** when ports or credentials need to be overridden:

To build and run the application and all dependencies in Docker, only Docker Compose v2 is required:

~~~bash
cp .env.example .env
docker compose up --build -d --wait
docker compose logs -f app
~~~

The API is exposed at **http://localhost:8080** by default. Run **docker compose down** to stop the stack. PostgreSQL, Redis, and RabbitMQ data remain in named volumes unless they are explicitly removed.

For a local Java development loop, JDK 25 and Bash are also required:

~~~bash
cp .env.example .env
./scripts/dev.sh
~~~

**dev.sh** starts PostgreSQL 18, Redis 8.2, and RabbitMQ 4.1 with Docker Compose, waits until their container health checks pass, then runs LedgerBank through **./mvnw spring-boot:run**. Stop the Java process with Ctrl+C; infrastructure remains available for the next run.

## Test commands

Fast unit and MVC tests:

~~~bash
./scripts/test.sh
~~~

Full verification with Testcontainers:

~~~bash
./scripts/integration-test.sh
~~~

The integration suite starts isolated PostgreSQL, Redis, and RabbitMQ containers on dynamic ports. Docker must be reachable, but the development Compose stack does not need to be running.

Run the same verification and quality gates used by CI:

~~~bash
./mvnw clean verify -Pintegration,quality
~~~

The quality profile enables all Java compiler lint warnings, Checkstyle, SpotBugs, and separate JaCoCo reports for unit/MVC and integration coverage. Generated reports are available under:

~~~text
target/checkstyle-result.xml
target/spotbugsXml.xml
target/site/jacoco/index.html
target/site/jacoco-it/index.html
target/surefire-reports/
target/failsafe-reports/
~~~

The last verified suite contains 95 unit/MVC tests and 24 integration tests. Integration tests create all dependencies through Testcontainers, apply Flyway V1-V8 from an empty PostgreSQL database, and isolate RabbitMQ queues between test methods.

Extra Maven arguments can be appended to either script:

~~~bash
./scripts/test.sh -Dtest=TransferServiceTest
~~~

## Health checks

Check all Compose dependencies and application liveness:

~~~bash
./scripts/health-check.sh
~~~

Use a non-default application URL without exposing a token:

~~~bash
LEDGERBANK_BASE_URL=http://localhost:9090 ./scripts/health-check.sh
~~~

To check only PostgreSQL, Redis, and RabbitMQ:

~~~bash
./scripts/health-check.sh --infrastructure-only
~~~

The script discards the liveness response body and never prints database, Redis, or RabbitMQ credentials.

## Safe local database reset

**db-reset.sh** deletes and recreates only the **ledgerbank_postgres_data** Docker volume in the explicit **ledgerbank** Compose project. It verifies the Compose volume labels and does not remove Redis or RabbitMQ data.

~~~bash
./scripts/db-reset.sh
~~~

The command prints the exact project, database, and volume, then requires the literal confirmation **RESET ledgerbank/ledgerbank**. It refuses any environment except **local** or **development**. Automation may pass **--yes** only after setting the environment explicitly:

~~~bash
LEDGERBANK_ENVIRONMENT=local ./scripts/db-reset.sh --yes
~~~

Never use this utility for a shared, staging, or production database.

## Linux, Git Bash, and WSL

On Linux/macOS, ensure the committed executable bits are preserved:

~~~bash
chmod +x mvnw scripts/*.sh
~~~

On Windows, run the shell files from Git Bash or WSL. Docker Desktop must be available to that environment; enable WSL integration when using WSL. Native PowerShell users can use **./mvnw.cmd** and **docker compose** directly, but the automation scripts themselves require Bash.

**.gitattributes** pins **mvnw** and every file in **scripts/** to LF, while **mvnw.cmd** remains CRLF. Do not convert Bash scripts to CRLF.

## Relevant environment variables

| Variable | Purpose | Default |
|---|---|---|
| **SPRING_PROFILES_ACTIVE** | Spring profile; local is the development default | **local** |
| **LEDGERBANK_ENVIRONMENT** | Safety scope used by db-reset | value of SPRING_PROFILES_ACTIVE, then local |
| **LEDGERBANK_BASE_URL** | Base URL checked by health-check | **http://localhost:${PORT:-8080}** |
| **PORT** | Application port | **8080** |
| **DB_*** | Local PostgreSQL connection/Compose port | see **.env.example** |
| **REDIS_*** | Local Redis connection | see **.env.example** |
| **RABBITMQ_*** | Local RabbitMQ connection | see **.env.example** |

Keep **.env** local. It is ignored by Git; never commit real credentials or tokens.

## Continuous integration

**.github/workflows/ci.yml** runs on pushes and pull requests using Ubuntu and Temurin Java 25. The build job validates the committed Maven Wrapper URL/checksum, packages the application, runs unit/MVC tests, and applies the quality profile. A dependent job runs the Testcontainers integration suite with Docker on the runner.

Failure reports are uploaded with short retention. Unit quality reports are uploaded on every build. The workflow uses read-only repository contents permission and cancels superseded runs for the same ref.

Local success proves the repository state and Docker-backed suite; it does not imply that an unpushed commit has passed remote GitHub Actions. Create a release tag only after the corresponding remote CI run passes on a clean tree.
