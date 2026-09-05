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
# Signing in is the probe: reads need a token, so an unauthenticated GET is
# rejected at the gateway without proving any downstream service is alive.
# Capped well under the gateway's 10-per-minute brake on POST /api/auth/**.
ready=""
for i in $(seq 1 9); do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"email":"tech@acme.example","password":"Passw0rd!"}')
  if [ "$code" = "200" ]; then ready=1; break; fi
  sleep 5
done
[ -n "$ready" ] || { echo "gateway not routing after 45s"; exit 1; }
echo "gateway ready"

step "Failure: read with no token -> 401 (nothing is public any more)"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/assets?clientId=$CLIENT")
echo "got $code"; [ "$code" = "401" ] || exit 1

step "Sign in as the seeded tech"
TOKEN=$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"tech@acme.example","password":"Passw0rd!"}' | j "['token']")
AUTH="Authorization: Bearer $TOKEN"
echo "token acquired"

step "Clients"
curl -fsS -H "$AUTH" "$BASE/api/clients"; echo

step "Assets in stock for client $CLIENT"
STOCK=$(curl -fsS -H "$AUTH" "$BASE/api/assets?clientId=$CLIENT&status=IN_STOCK")
ASSET=$(echo "$STOCK" | j "[0]['id']")
echo "picked asset $ASSET"

step "All laptops"
echo "$(curl -fsS -H "$AUTH" "$BASE/api/assets?clientId=$CLIENT&type=Laptop" | j "[len(d)]") laptops"

step "People"
PEOPLE=$(curl -fsS -H "$AUTH" "$BASE/api/people?clientId=$CLIENT")
PERSON=$(echo "$PEOPLE" | j "[0]['id']")
echo "picked person $PERSON"

step "Desks"
echo "$(curl -fsS -H "$AUTH" "$BASE/api/locations?clientId=$CLIENT&kind=DESK" | j "[len(d)]") desks"

step "Check asset $ASSET out to person $PERSON"
ASSIGN=$(curl -fsS -X POST "$BASE/api/assignments" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"clientId\":$CLIENT,\"assetId\":$ASSET,\"holderType\":\"PERSON\",\"holderId\":$PERSON}")
echo "$ASSIGN"
OPEN=$(echo "$ASSIGN" | j "['open']")
ACTOR=$(echo "$ASSIGN" | j "['checkedOutBy']")
[ "$OPEN" = "True" ] || { echo "expected open assignment"; exit 1; }
[ "$ACTOR" = "tech@acme.example" ] || { echo "expected checkedOutBy=tech@acme.example, got $ACTOR"; exit 1; }

step "It shows on the person"
curl -fsS -H "$AUTH" "$BASE/api/assets?clientId=$CLIENT&holderType=PERSON&holderId=$PERSON" | j "[[a['assetTag'] for a in d]]"

step "An ordinary employee sees only their own gear"
HERS=$(curl -fsS -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"dana.reyes@acme.example","password":"Passw0rd!"}' | j "['token']")
MINE=$(curl -fsS -H "Authorization: Bearer $HERS" "$BASE/api/assets?clientId=$CLIENT" | j "[len(d)]")
ALL=$(curl -fsS -H "$AUTH" "$BASE/api/assets?clientId=$CLIENT" | j "[len(d)]")
echo "Dana sees $MINE of the tenant's $ALL assets"
[ "$MINE" -lt "$ALL" ] || { echo "expected the employee's view to be narrower"; exit 1; }

step "Failure: check it out again -> 409"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/assignments" -H "$AUTH" \
  -H 'Content-Type: application/json' \
  -d "{\"clientId\":$CLIENT,\"assetId\":$ASSET,\"holderType\":\"PERSON\",\"holderId\":$PERSON}")
echo "got $code"; [ "$code" = "409" ] || exit 1

step "Offboard person $PERSON"
RESULT=$(curl -fsS -X POST "$BASE/api/assignments/offboard?clientId=$CLIENT&personId=$PERSON" -H "$AUTH")
echo "$RESULT"

step "Asset $ASSET is back in stock"
ST=$(curl -fsS -H "$AUTH" "$BASE/api/assets/$ASSET" | j "['status']")
echo "status = $ST"; [ "$ST" = "IN_STOCK" ] || exit 1

step "Notifications for client $CLIENT"
curl -fsS -H "$AUTH" "$BASE/api/notifications?clientId=$CLIENT" | j "[[n['type'] for n in d]]"

printf '\nAll demo steps passed.\n'
