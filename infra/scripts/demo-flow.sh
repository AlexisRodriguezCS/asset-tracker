#!/usr/bin/env bash
# End-to-end demo through the API gateway. Needs curl + python (3 or 2.7).
#   bash infra/scripts/demo-flow.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"
CLIENT=1

PY="$(command -v python3 || command -v python || true)"
[ -n "$PY" ] || { echo "need python on PATH"; exit 1; }
j() { "$PY" -c "import sys,json;d=json.load(sys.stdin);print(d$1)"; }
step() { printf '\n=== %s ===\n' "$1"; }

step "Wait for the gateway to route (lb:// routes activate after the first Eureka fetch)"
ready=""
for i in $(seq 1 45); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/assets?clientId=$CLIENT")
  if [ "$code" = "200" ]; then ready=1; break; fi
  sleep 2
done
[ -n "$ready" ] || { echo "gateway not routing after 90s"; exit 1; }
echo "gateway ready"

step "Clients (public)"
curl -fsS "$BASE/api/clients"; echo

step "Assets in stock for client $CLIENT (public)"
STOCK=$(curl -fsS "$BASE/api/assets?clientId=$CLIENT&status=IN_STOCK")
ASSET=$(echo "$STOCK" | j "[0]['id']")
echo "picked asset $ASSET"

step "All laptops (public)"
curl -fsS "$BASE/api/assets?clientId=$CLIENT&type=Laptop" | j "[len(d)]" >/dev/null \
  && echo "$(curl -fsS "$BASE/api/assets?clientId=$CLIENT&type=Laptop" | j "[len(d)]") laptops"

step "People (public)"
PEOPLE=$(curl -fsS "$BASE/api/people?clientId=$CLIENT")
PERSON=$(echo "$PEOPLE" | j "[0]['id']")
echo "picked person $PERSON"

step "Desks (public)"
echo "$(curl -fsS "$BASE/api/locations?clientId=$CLIENT&kind=DESK" | j "[len(d)]") desks"

step "Failure: check-out with no token -> 401"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/assignments" \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\":$CLIENT,\"assetId\":$ASSET,\"holderType\":\"PERSON\",\"holderId\":$PERSON}")
echo "got $code"; [ "$code" = "401" ] || exit 1

step "Sign in as the seeded tech"
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"tech@acme.example","password":"Passw0rd!"}' | j "['token']")
AUTH="Authorization: Bearer $TOKEN"
echo "token acquired"

step "Check asset $ASSET out to person $PERSON"
ASSIGN=$(curl -fsS -X POST "$BASE/api/assignments" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"clientId\":$CLIENT,\"assetId\":$ASSET,\"holderType\":\"PERSON\",\"holderId\":$PERSON}")
echo "$ASSIGN"
OPEN=$(echo "$ASSIGN" | j "['open']")
ACTOR=$(echo "$ASSIGN" | j "['checkedOutBy']")
[ "$OPEN" = "True" ] || { echo "expected open assignment"; exit 1; }
[ "$ACTOR" = "tech@acme.example" ] || { echo "expected checkedOutBy=tech@acme.example, got $ACTOR"; exit 1; }

step "It shows on the person"
curl -fsS "$BASE/api/assets?clientId=$CLIENT&holderType=PERSON&holderId=$PERSON" | j "[[a['assetTag'] for a in d]]"

step "Failure: check it out again -> 409"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/assignments" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\":$CLIENT,\"assetId\":$ASSET,\"holderType\":\"PERSON\",\"holderId\":$PERSON}")
echo "got $code"; [ "$code" = "409" ] || exit 1

step "Offboard person $PERSON"
RESULT=$(curl -fsS -X POST "$BASE/api/assignments/offboard?clientId=$CLIENT&personId=$PERSON" -H "$AUTH")
echo "$RESULT"

step "Asset $ASSET is back in stock"
ST=$(curl -fsS "$BASE/api/assets/$ASSET" | j "['status']")
echo "status = $ST"; [ "$ST" = "IN_STOCK" ] || exit 1

step "Notifications for client $CLIENT (public)"
curl -fsS "$BASE/api/notifications?clientId=$CLIENT" | j "[[n['type'] for n in d]]"

printf '\nAll demo steps passed.\n'
