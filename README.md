# Wallet & P2P Transfer Service

A Java 21 / Spring Boot p2p money transfer wallet service. Its persistence layer uses Spring Data JPA, with Postgres conditional updates inside one transaction to prevent overdrafts, a database uniqueness constraint for idempotency, Flyway migrations, Actuator/Micrometer telemetry, and a minimal UI for monitoring.

## Run locally

Prerequisites: Docker Desktop only. No database installation needed — `docker-compose.yml` spins up a fresh local Postgres container alongside the app. The DB credentials in that file (`wallet/wallet`) are invented for this local container and have no relation to any deployed database.

```bash
docker compose up --build
```

That single command builds the app image and starts both Postgres and the Spring Boot service. Spring Boot reads the datasource connection from the env vars docker-compose injects directly — `application.yml` is not required for this to work. If you want to run the app without Docker (e.g. `mvn spring-boot:run`), copy `src/main/resources/application.yml.example` to `src/main/resources/application.yml` and fill in your own values.

The API is at `http://localhost:8080`. Key endpoints:

| Endpoint | Description |
| --- | --- |
| `GET /dashboard.html` | Live metrics dashboard (polls Prometheus every 10 s) |
| `GET /actuator/health` | Service health status |
| `GET /actuator/info` | Build / version info |
| `GET /actuator/metrics` | Micrometer metrics index |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |

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

## Deployed instance

The service is live at **https://paytm-wallet-q6fk.onrender.com**

> **Warning — cold start:** Render's free tier spins the service down after 15 minutes of inactivity. Before running any test scripts against the deployed URL, hit the health endpoint first and wait for a `200` response (may take up to 60 seconds):
>
> ```bash
> curl https://paytm-wallet-q6fk.onrender.com/actuator/health
> ```

Deployed endpoints:

| Endpoint | URL |
| --- | --- |
| Dashboard | https://paytm-wallet-q6fk.onrender.com/dashboard.html |
| Health | https://paytm-wallet-q6fk.onrender.com/actuator/health |
| Info | https://paytm-wallet-q6fk.onrender.com/actuator/info |
| Metrics | https://paytm-wallet-q6fk.onrender.com/actuator/metrics |
| Prometheus | https://paytm-wallet-q6fk.onrender.com/actuator/prometheus |

## Automated probe scripts

Make scripts executable once before running anything:

```bash
chmod +x scripts/*.sh
```

### Against the deployed service (pre-funded wallets ready)

The wallets below already exist in the deployed database with real balances — no setup needed. Wake the service first (see cold-start warning above), then run:

```bash
BASE=https://paytm-wallet-q6fk.onrender.com
ALICE=ef944f68-2119-4f2e-872b-ff32fa2a1ff5
BOB=57645ae4-73a3-46e7-a0b7-4c975667daa9
TREASURY=1e3a26a1-2bb3-4464-83bb-020cf179d21c

scripts/concurrent-wallet-create.sh $BASE token-alice 20
scripts/idempotent-retry-storm.sh $BASE token-alice $ALICE $BOB 100 15
scripts/conservation-under-contention.sh $BASE $ALICE:token-alice,$BOB:token-bob,$TREASURY:token-treasury 100
```

### Against a local Docker Compose instance (fresh database)

With Docker Compose the database starts empty, so you must create and fund wallets before the scripts can run anything meaningful. Start the stack first:

```bash
docker compose up --build
```

Then create all three wallets and capture the IDs:

```bash
ALICE=$(curl -s -X POST http://localhost:8080/wallets \
  -H 'Authorization: Bearer token-alice' | grep -o '"wallet_id":"[^"]*"' | cut -d'"' -f4)

BOB=$(curl -s -X POST http://localhost:8080/wallets \
  -H 'Authorization: Bearer token-bob' | grep -o '"wallet_id":"[^"]*"' | cut -d'"' -f4)

TREASURY=$(curl -s -X POST http://localhost:8080/wallets \
  -H 'Authorization: Bearer token-treasury' | grep -o '"wallet_id":"[^"]*"' | cut -d'"' -f4)
```

Fund Alice from the treasury (pre-seeded with `100000000000` paise):

```bash
curl -s -X POST http://localhost:8080/transfers \
  -H 'Authorization: Bearer token-treasury' -H 'Content-Type: application/json' \
  -d "{\"from\":\"$TREASURY\",\"to\":\"$ALICE\",\"amount_paise\":5000000,\"idempotency_key\":\"fund-alice-init\"}"
```

Now run the scripts:

```bash
BASE=http://localhost:8080
scripts/concurrent-wallet-create.sh $BASE token-alice 20
scripts/idempotent-retry-storm.sh $BASE token-alice $ALICE $BOB 100 15
scripts/conservation-under-contention.sh $BASE $ALICE:token-alice,$BOB:token-bob,$TREASURY:token-treasury 100
```

For `conservation-under-contention.sh`, each `WALLET_ID:TOKEN` pair must use the token that owns that wallet. The scripts exit non-zero when the claimed invariant fails.
