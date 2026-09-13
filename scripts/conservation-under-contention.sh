#!/usr/bin/env bash
set -euo pipefail
base_url=${1:?"usage: conservation-under-contention.sh BASE_URL WALLET_ID:TOKEN,WALLET_ID:TOKEN [ROUNDS]"}
IFS=',' read -r -a pairs <<< "${2:?}"; rounds=${3:-100}; temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT
wallets=(); tokens=()
for pair in "${pairs[@]}"; do wallets+=("${pair%%:*}"); tokens+=("${pair#*:}"); done
total() { local sum=0; for i in "${!wallets[@]}"; do balance=$(curl -fsS "$base_url/wallets/${wallets[$i]}" -H "Authorization: Bearer ${tokens[$i]}" | sed -n 's/.*"balance_paise":\([0-9]*\).*/\1/p'); sum=$((sum + balance)); done; echo "$sum"; }
before=$(total)
for i in $(seq 1 "$rounds"); do
  from_index=$((RANDOM % ${#wallets[@]})); to_index=$((RANDOM % ${#wallets[@]})); from=${wallets[$from_index]}; to=${wallets[$to_index]}; while [ "$from" = "$to" ]; do to_index=$((RANDOM % ${#wallets[@]})); to=${wallets[$to_index]}; done
  amount=$((RANDOM % 500 + 1)); key="contention-$i-$RANDOM"
  body=$(printf '{"from":"%s","to":"%s","amount_paise":%s,"idempotency_key":"%s"}' "$from" "$to" "$amount" "$key")
  curl -fsS -X POST "$base_url/transfers" -H "Authorization: Bearer ${tokens[$from_index]}" -H 'Content-Type: application/json' -d "$body" -o "$temp_dir/result_$i.json" &
  while [ "$(jobs -r | wc -l)" -ge 10 ]; do sleep 0.1; done
done
wait
after=$(total); echo "before=$before after=$after"
test "$before" = "$after" && echo "PASS: conservation held" || { echo "FAIL"; exit 1; }
