#!/usr/bin/env bash
set -euo pipefail
base_url=${1:?"usage: concurrent-wallet-create.sh BASE_URL TOKEN [N]"}
token=${2:?"Bearer token required"}
n=${3:-20}
temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
for i in $(seq 1 "$n"); do
  curl -fsS -X POST "$base_url/wallets" -H "Authorization: Bearer $token" -o "$temp_dir/result_$i.json" &
done
wait
unique=$(grep -oh '"wallet_id":"[^"]*"' "$temp_dir"/*.json | sort -u | wc -l | tr -d ' ')
echo "distinct wallet IDs: $unique"
test "$unique" = 1 && echo "PASS" || { echo "FAIL"; exit 1; }
