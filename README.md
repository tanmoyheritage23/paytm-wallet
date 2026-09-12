# Wallet & P2P Transfer Service

A Java 21 / Spring Boot p2p money transfer wallet service. Its persistence layer uses Spring Data JPA, with Postgres conditional updates inside one transaction to prevent overdrafts, a database uniqueness constraint for idempotency, Flyway migrations, Actuator/Micrometer telemetry, and a minimal React UI.

## Run locally

Prerequisites: Docker Desktop only. No database installation needed — `docker-compose.yml` spins up a fresh local Postgres container alongside the app. The DB credentials in that file (`wallet/wallet`) are invented for this local container and have no relation to any deployed database.

```bash
docker compose up --build
```

That single command builds the app image and starts both Postgres and the Spring Boot service. Spring Boot reads the datasource connection from the env vars docker-compose injects directly — `application.yml` is not required for this to work. If you want to run the app without Docker (e.g. `mvn spring-boot:run`), copy `src/main/resources/application.yml.example` to `src/main/resources/application.yml` and fill in your own values.

The API is at `http://localhost:8080`; health is at `http://localhost:8080/actuator/health`; the built-in metrics view is `http://localhost:8080/dashboard.html`.

The local demo tokens are deliberately non-secret:

| Token | User |
| --- | --- |
| `token-alice` | `alice` |
| `token-bob` | `bob` |
| `token-treasury` | `system-treasury` |

Create Alice and Bob wallets:

```bash
curl -X POST http://localhost:8080/wallets -H 'Authorization: Bearer token-alice'
curl -X POST http://localhost:8080/wallets -H 'Authorization: Bearer token-bob'
```

The `system-treasury` wallet is seeded with `100000000000` paise. Obtain its ID with `POST /wallets` under `token-treasury`, then fund Alice using that token. Use the returned Alice wallet ID as `to`.

```bash
curl -X POST http://localhost:8080/wallets -H 'Authorization: Bearer token-treasury'
curl -X POST http://localhost:8080/transfers \
  -H 'Authorization: Bearer token-treasury' -H 'Content-Type: application/json' \
  -d '{"from":"TREASURY_ID","to":"ALICE_ID","amount_paise":50000,"idempotency_key":"fund-alice-001"}'
```

Then send money as Alice:

```bash
curl -X POST http://localhost:8080/transfers \
  -H 'Authorization: Bearer token-alice' -H 'Content-Type: application/json' \
  -d '{"from":"ALICE_ID","to":"BOB_ID","amount_paise":1250,"idempotency_key":"alice-to-bob-001"}'
```

Repeat the final command unchanged: it must return `200` and the same `transfer_id` with `idempotent_replay: true`. Change the amount while retaining the idempotency key: it must return `409`.

## Run the React UI

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the displayed Vite URL, normally `http://localhost:5173`. Click **Create / load my wallet** while using `token-alice`; paste wallet IDs to perform transfers. The intentionally simple UI avoids hiding the important idempotency key behavior.

## Automated probe scripts

After creating/funding wallets, make scripts executable once (`chmod +x scripts/*.sh`) and run:

```bash
scripts/concurrent-wallet-create.sh http://localhost:8080 token-alice 20
scripts/idempotent-retry-storm.sh http://localhost:8080 token-alice ALICE_ID BOB_ID 100 15
scripts/conservation-under-contention.sh http://localhost:8080 ALICE_ID:token-alice,BOB_ID:token-bob,TREASURY_ID:token-treasury 100
```

For the final command, each `WALLET_ID:TOKEN` pair uses the token that owns that wallet. Fund the wallets first. The scripts exit non-zero when the claimed invariant fails.
