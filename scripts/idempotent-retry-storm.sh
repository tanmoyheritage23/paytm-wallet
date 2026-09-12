#!/usr/bin/env bash
set -euo pipefail
base_url=${1:?"usage: idempotent-retry-storm.sh BASE_URL TOKEN FROM TO [AMOUNT] [N]"}
token=${2:?}; from=${3:?}; to=${4:?}; amount=${5:-1000}; n=${6:-15}
key="retry-storm-$(date +%s)"; temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
body=$(printf '{"from":"%s","to":"%s","amount_paise":%s,"idempotency_key":"%s"}' "$from" "$to" "$amount" "$key")
for i in $(seq 1 "$n"); do
  curl -fsS -X POST "$base_url/transfers" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body" -o "$temp_dir/result_$i.json" &
done
wait
unique=$(grep -oh '"transfer_id":"[^"]*"' "$temp_dir"/*.json | sort -u | wc -l | tr -d ' ')
echo "distinct transfer IDs: $unique (from $n attempts)"
test "$unique" = 1 && echo "PASS" || { echo "FAIL"; exit 1; }
