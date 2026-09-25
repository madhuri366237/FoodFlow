# FoodFlow Backend

Spring Boot 3.5 REST API (Java 21). The project overview is in the [root README](../README.md).

## Commands

Run these from `backend/`. On Windows use `mvnw.cmd` instead of `./mvnw`.

| Command | What it does |
|---|---|
| `./mvnw spring-boot:run` | API on :8080; needs PostgreSQL and `../.env` |
| `./mvnw test` | All tests. Needs **Docker running** (Testcontainers starts a throwaway PostgreSQL) |
| `./mvnw verify` | Tests + JaCoCo coverage report at `target/site/jacoco/index.html` |
| `./mvnw package -DskipTests` | Builds `target/foodflow-backend-*.jar` |

## Layout and rules

- `controller → service → repository`. Controllers never touch repositories; entities never leave services (DTOs only).
- **Business rules live in services and entities.** For example `Order.changeStatus` enforces the state machine, `Coupon.calculateDiscount` does the discount arithmetic, and `Cart` guards its own invariant.
- **Transactions are opened in services** (`@Transactional`). External calls such as the payment gateway always happen *outside* them (see `PaymentServiceImpl`).
- **Schema changes are new Flyway files** (`V11__...sql`). Never edit an applied migration.
- **Configuration comes from environment variables** (see `application.yml`). Secrets have no defaults.
- **Ownership checks:** `RestaurantAccess` (403, public data) and `OrderAccess` (404, private data).

## Configuration files

| File | Purpose |
|---|---|
| `src/main/resources/application.yml` | Real configuration; secrets are `${ENV_VAR}` placeholders; imports `.env` for local runs |
| `src/test/resources/application-test.yml` | Test profile: pins every `app.*` value so tests never depend on a developer's `.env` |
| `src/main/resources/db/migration/` | Flyway migrations V1–V10, with the reasoning for each constraint and index |

## Windows troubleshooting: "Unable to establish loopback connection"

Some Windows security software blocks Unix-domain socket files under `AppData\Local`, which Java uses internally, so Tomcat fails to start. Point Java at another folder:

```bash
mkdir C:\Users\Public\java-uds
./mvnw spring-boot:run "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:\Users\Public\java-uds"
```

To make this permanent, set the user environment variable `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Public\java-uds`. Inside Docker (Linux) this isn't needed.
