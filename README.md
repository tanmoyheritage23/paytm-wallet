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

## Postman collection

Import `postman_collection.json` (in the repo root) into Postman, or paste the JSON below directly via **Import → Raw text**.

Collection variables are pre-filled with the deployed wallet IDs so you can run every request immediately. To use the local Docker Compose instance instead, change `base_url` in the collection variables to `http://localhost:8080` and run the wallet-create requests first so the ID variables are populated.

Run order: **Health & Observability → Wallets → Transfers — Happy Path → Transfers — Error Cases**. The happy-path transfers folder saves `transfer_id` as a collection variable for the error-case folder to reuse.

<details>
<summary>postman_collection.json</summary>

```json
{
  "info": {
    "_postman_id": "wallet-transfer-api",
    "name": "Wallet Transfer API",
    "description": "Full test suite for the Paytm Wallet P2P Transfer service. Collection variables are pre-filled with deployed wallet IDs. Run requests in order inside each folder — the Transfers folder relies on wallet IDs set by the Wallets folder.",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    { "key": "base_url",          "value": "https://paytm-wallet-q6fk.onrender.com", "type": "string" },
    { "key": "alice_token",       "value": "token-alice",                             "type": "string" },
    { "key": "bob_token",         "value": "token-bob",                               "type": "string" },
    { "key": "treasury_token",    "value": "token-treasury",                          "type": "string" },
    { "key": "alice_wallet_id",   "value": "ef944f68-2119-4f2e-872b-ff32fa2a1ff5",   "type": "string" },
    { "key": "bob_wallet_id",     "value": "57645ae4-73a3-46e7-a0b7-4c975667daa9",   "type": "string" },
    { "key": "treasury_wallet_id","value": "1e3a26a1-2bb3-4464-83bb-020cf179d21c",   "type": "string" },
    { "key": "transfer_id",       "value": "",                                        "type": "string" }
  ],
  "item": [
    {
      "name": "Health & Observability",
      "item": [
        {
          "name": "Health Check",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/actuator/health",
              "host": ["{{base_url}}"],
              "path": ["actuator", "health"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200', () => pm.response.to.have.status(200));",
                  "pm.test('Service is UP', () => {",
                  "  pm.expect(pm.response.json().status).to.equal('UP');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Info",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/actuator/info",
              "host": ["{{base_url}}"],
              "path": ["actuator", "info"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
              }
            }
          ]
        },
        {
          "name": "Metrics Index",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/actuator/metrics",
              "host": ["{{base_url}}"],
              "path": ["actuator", "metrics"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": ["pm.test('Status 200', () => pm.response.to.have.status(200));"]
              }
            }
          ]
        },
        {
          "name": "Prometheus Scrape",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/actuator/prometheus",
              "host": ["{{base_url}}"],
              "path": ["actuator", "prometheus"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200', () => pm.response.to.have.status(200));",
                  "pm.test('Contains wallet metrics', () => {",
                  "  pm.expect(pm.response.text()).to.include('wallet');",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    },
    {
      "name": "Wallets",
      "item": [
        {
          "name": "Create / Get Alice Wallet",
          "request": {
            "method": "POST",
            "header": [{ "key": "Authorization", "value": "Bearer {{alice_token}}" }],
            "url": { "raw": "{{base_url}}/wallets", "host": ["{{base_url}}"], "path": ["wallets"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 or 201', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
                  "pm.test('Has wallet_id', () => {",
                  "  const json = pm.response.json();",
                  "  pm.expect(json.wallet_id).to.be.a('string');",
                  "  pm.collectionVariables.set('alice_wallet_id', json.wallet_id);",
                  "});",
                  "pm.test('user_id is alice', () => {",
                  "  pm.expect(pm.response.json().user_id).to.equal('alice');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Create / Get Bob Wallet",
          "request": {
            "method": "POST",
            "header": [{ "key": "Authorization", "value": "Bearer {{bob_token}}" }],
            "url": { "raw": "{{base_url}}/wallets", "host": ["{{base_url}}"], "path": ["wallets"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 or 201', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
                  "pm.test('Has wallet_id', () => {",
                  "  const json = pm.response.json();",
                  "  pm.expect(json.wallet_id).to.be.a('string');",
                  "  pm.collectionVariables.set('bob_wallet_id', json.wallet_id);",
                  "});",
                  "pm.test('user_id is bob', () => {",
                  "  pm.expect(pm.response.json().user_id).to.equal('bob');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Create / Get Treasury Wallet",
          "request": {
            "method": "POST",
            "header": [{ "key": "Authorization", "value": "Bearer {{treasury_token}}" }],
            "url": { "raw": "{{base_url}}/wallets", "host": ["{{base_url}}"], "path": ["wallets"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 or 201', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
                  "pm.test('Has wallet_id', () => {",
                  "  const json = pm.response.json();",
                  "  pm.expect(json.wallet_id).to.be.a('string');",
                  "  pm.collectionVariables.set('treasury_wallet_id', json.wallet_id);",
                  "});",
                  "pm.test('user_id is system-treasury', () => {",
                  "  pm.expect(pm.response.json().user_id).to.equal('system-treasury');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Wallet by ID",
          "request": {
            "method": "GET",
            "header": [{ "key": "Authorization", "value": "Bearer {{alice_token}}" }],
            "url": {
              "raw": "{{base_url}}/wallets/{{alice_wallet_id}}",
              "host": ["{{base_url}}"],
              "path": ["wallets", "{{alice_wallet_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200', () => pm.response.to.have.status(200));",
                  "pm.test('Correct wallet returned', () => {",
                  "  const json = pm.response.json();",
                  "  pm.expect(json.wallet_id).to.equal(pm.collectionVariables.get('alice_wallet_id'));",
                  "  pm.expect(json.balance_paise).to.be.a('number');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Wallet — 403 Wrong Owner",
          "request": {
            "method": "GET",
            "header": [{ "key": "Authorization", "value": "Bearer {{bob_token}}" }],
            "url": {
              "raw": "{{base_url}}/wallets/{{alice_wallet_id}}",
              "host": ["{{base_url}}"],
              "path": ["wallets", "{{alice_wallet_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 403', () => pm.response.to.have.status(403));",
                  "pm.test('Error code wallet_forbidden', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('wallet_forbidden');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Wallet — 404 Not Found",
          "request": {
            "method": "GET",
            "header": [{ "key": "Authorization", "value": "Bearer {{alice_token}}" }],
            "url": {
              "raw": "{{base_url}}/wallets/00000000-0000-0000-0000-000000000000",
              "host": ["{{base_url}}"],
              "path": ["wallets", "00000000-0000-0000-0000-000000000000"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 404', () => pm.response.to.have.status(404));",
                  "pm.test('Error code wallet_not_found', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('wallet_not_found');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Wallet — 401 No Auth",
          "request": {
            "method": "GET",
            "header": [],
            "url": {
              "raw": "{{base_url}}/wallets/{{alice_wallet_id}}",
              "host": ["{{base_url}}"],
              "path": ["wallets", "{{alice_wallet_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": ["pm.test('Status 401', () => pm.response.to.have.status(401));"]
              }
            }
          ]
        }
      ]
    },
    {
      "name": "Transfers — Happy Path",
      "item": [
        {
          "name": "Fund Alice from Treasury",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization",  "value": "Bearer {{treasury_token}}" },
              { "key": "Content-Type",   "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{treasury_wallet_id}}\",\n  \"to\": \"{{alice_wallet_id}}\",\n  \"amount_paise\": 50000,\n  \"idempotency_key\": \"fund-alice-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 or 201', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
                  "pm.test('Has transfer_id', () => pm.expect(pm.response.json().transfer_id).to.be.a('string'));",
                  "pm.test('Status COMPLETED or DECLINED', () => {",
                  "  pm.expect(pm.response.json().status).to.be.oneOf(['COMPLETED', 'DECLINED']);",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Alice → Bob (New Transfer)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 500,\n  \"idempotency_key\": \"alice-bob-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 or 201', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
                  "pm.test('Has transfer_id — store for later tests', () => {",
                  "  const json = pm.response.json();",
                  "  pm.expect(json.transfer_id).to.be.a('string');",
                  "  pm.collectionVariables.set('transfer_id', json.transfer_id);",
                  "});",
                  "pm.test('Amount is 500 paise', () => {",
                  "  pm.expect(pm.response.json().amount_paise).to.equal(500);",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Alice → Bob (Idempotent Replay — same body)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 500,\n  \"idempotency_key\": \"alice-bob-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 (replay)', () => pm.response.to.have.status(200));",
                  "pm.test('idempotent_replay is true', () => {",
                  "  pm.expect(pm.response.json().idempotent_replay).to.equal(true);",
                  "});",
                  "pm.test('Same transfer_id as first request', () => {",
                  "  pm.expect(pm.response.json().transfer_id).to.equal(pm.collectionVariables.get('transfer_id'));",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "Get Transfer by ID",
          "request": {
            "method": "GET",
            "header": [{ "key": "Authorization", "value": "Bearer {{alice_token}}" }],
            "url": {
              "raw": "{{base_url}}/transfers/{{transfer_id}}",
              "host": ["{{base_url}}"],
              "path": ["transfers", "{{transfer_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200', () => pm.response.to.have.status(200));",
                  "pm.test('Correct transfer returned', () => {",
                  "  pm.expect(pm.response.json().transfer_id).to.equal(pm.collectionVariables.get('transfer_id'));",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    },
    {
      "name": "Transfers — Error Cases",
      "item": [
        {
          "name": "409 Idempotency Conflict (same key, different amount)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 9999,\n  \"idempotency_key\": \"alice-bob-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 409', () => pm.response.to.have.status(409));",
                  "pm.test('Error code idempotency_key_conflict', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('idempotency_key_conflict');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "DECLINED — Insufficient Funds",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 999999999999,\n  \"idempotency_key\": \"alice-overdraft-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 200 or 201', () => pm.expect(pm.response.code).to.be.oneOf([200, 201]));",
                  "pm.test('Transfer is DECLINED', () => {",
                  "  pm.expect(pm.response.json().status).to.equal('DECLINED');",
                  "});",
                  "pm.test('Decline reason is INSUFFICIENT_FUNDS', () => {",
                  "  pm.expect(pm.response.json().decline_reason).to.equal('INSUFFICIENT_FUNDS');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "400 Self Transfer",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{alice_wallet_id}}\",\n  \"amount_paise\": 100,\n  \"idempotency_key\": \"self-transfer-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 400', () => pm.response.to.have.status(400));",
                  "pm.test('Error code self_transfer', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('self_transfer');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "403 Wrong Token (Bob spends Alice's wallet)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{bob_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 100,\n  \"idempotency_key\": \"wrong-token-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 403', () => pm.response.to.have.status(403));",
                  "pm.test('Error code source_wallet_forbidden', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('source_wallet_forbidden');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "400 Validation Error (missing amount_paise)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"idempotency_key\": \"missing-amount-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 400', () => pm.response.to.have.status(400));",
                  "pm.test('Error code validation_error', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('validation_error');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "400 Validation Error (amount_paise = 0)",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 0,\n  \"idempotency_key\": \"zero-amount-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 400', () => pm.response.to.have.status(400));",
                  "pm.test('Error code validation_error', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('validation_error');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "400 Unknown Destination Wallet",
          "request": {
            "method": "POST",
            "header": [
              { "key": "Authorization", "value": "Bearer {{alice_token}}" },
              { "key": "Content-Type",  "value": "application/json" }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"00000000-0000-0000-0000-000000000000\",\n  \"amount_paise\": 100,\n  \"idempotency_key\": \"unknown-wallet-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 400', () => pm.response.to.have.status(400));",
                  "pm.test('Error code unknown_wallet', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('unknown_wallet');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "401 No Authorization Header",
          "request": {
            "method": "POST",
            "header": [{ "key": "Content-Type", "value": "application/json" }],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"from\": \"{{alice_wallet_id}}\",\n  \"to\": \"{{bob_wallet_id}}\",\n  \"amount_paise\": 100,\n  \"idempotency_key\": \"no-auth-postman-001\"\n}"
            },
            "url": { "raw": "{{base_url}}/transfers", "host": ["{{base_url}}"], "path": ["transfers"] }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 401', () => pm.response.to.have.status(401));",
                  "pm.test('Error code unauthorized', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('unauthorized');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "404 Transfer Not Found",
          "request": {
            "method": "GET",
            "header": [{ "key": "Authorization", "value": "Bearer {{alice_token}}" }],
            "url": {
              "raw": "{{base_url}}/transfers/00000000-0000-0000-0000-000000000000",
              "host": ["{{base_url}}"],
              "path": ["transfers", "00000000-0000-0000-0000-000000000000"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 404', () => pm.response.to.have.status(404));",
                  "pm.test('Error code transfer_not_found', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('transfer_not_found');",
                  "});"
                ]
              }
            }
          ]
        },
        {
          "name": "403 Get Transfer — Wrong Owner",
          "request": {
            "method": "GET",
            "header": [{ "key": "Authorization", "value": "Bearer {{bob_token}}" }],
            "url": {
              "raw": "{{base_url}}/transfers/{{transfer_id}}",
              "host": ["{{base_url}}"],
              "path": ["transfers", "{{transfer_id}}"]
            }
          },
          "event": [
            {
              "listen": "test",
              "script": {
                "type": "text/javascript",
                "exec": [
                  "pm.test('Status 403', () => pm.response.to.have.status(403));",
                  "pm.test('Error code transfer_forbidden', () => {",
                  "  pm.expect(pm.response.json().error).to.equal('transfer_forbidden');",
                  "});"
                ]
              }
            }
          ]
        }
      ]
    }
  ]
}
```

</details>

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
scripts/conservation-under-contention.sh $BASE ${ALICE}:token-alice,${BOB}:token-bob,${TREASURY}:token-treasury 100
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
scripts/conservation-under-contention.sh $BASE ${ALICE}:token-alice,${BOB}:token-bob,${TREASURY}:token-treasury 100
```

For `conservation-under-contention.sh`, each `WALLET_ID:TOKEN` pair must use the token that owns that wallet. The scripts exit non-zero when the claimed invariant fails.
