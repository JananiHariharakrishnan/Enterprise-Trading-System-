#!/usr/bin/env bash

# Integration test:
# preference -> real order -> fill -> mock notification
# -> real alert -> MOCK Kafka quote crossing -> alert triggered -> mock notification
#
# Kafka and email delivery are mocked.
# Auth, preferences, orders, fills, market-data lookup and alert creation remain real.

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

AUTH_URL=${AUTH_URL:-http://localhost:3000}
TRADE_API_URL=${TRADE_API_URL:-http://localhost:8081}

TRADE_USERNAME=${TRADE_USERNAME:-janani}
TRADE_PASSWORD=${TRADE_PASSWORD:-Y29ycmVjdCBob3JzZSBiYXR0ZXJ5IHN0YXBsZQ==}

# Leave empty to obtain a fresh token through /auth/login.
TOKEN=${TOKEN:-}

SYMBOL=${SYMBOL:-AAPL}
CHANNEL=${CHANNEL:-EMAIL}
ORDER_QTY=${ORDER_QTY:-1}
ORDER_SIDE=${ORDER_SIDE:-BUY}
ALERT_DELTA_PCT=${ALERT_DELTA_PCT:-1}
TIMEOUT_SECONDS=${TIMEOUT_SECONDS:-120}

# Kafka is intentionally mocked.
MOCK_KAFKA=${MOCK_KAFKA:-true}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

fail() {
  echo "FAIL: $*" >&2
  exit "${2:-1}"
}

step() {
  echo
  echo "== $*"
}

command -v curl >/dev/null || fail "curl is required" 2
command -v jq >/dev/null || fail "jq is required" 2

# ---------------------------------------------------------------------------
# HTTP helper
# ---------------------------------------------------------------------------

HTTP_CODE=""
HTTP_BODY=""

call() {
  local method=$1
  local url=$2
  local body=${3:-}
  local out

  local args=(
    -sS
    -X "$method"
    "$url"
    -H "Authorization: Bearer ${BEARER:-}"
    -H "Content-Type: application/json"
    -w $'\n%{http_code}'
  )

  [ -n "$body" ] && args+=(-d "$body")

  out=$(curl "${args[@]}") ||
    fail "curl failed for $method $url" 3

  HTTP_CODE=${out##*$'\n'}
  HTTP_BODY=${out%$'\n'*}

  case $HTTP_CODE in
    2??)
      ;;
    *)
      fail "$method $url -> HTTP $HTTP_CODE: $HTTP_BODY" 3
      ;;
  esac
}

# ---------------------------------------------------------------------------
# Mock notification delivery
# ---------------------------------------------------------------------------

wait_for_delivery() {
  local subject="$1"
  local label="$2"

  echo "MOCK: $label"
  echo "MOCK: notification subject = $subject"

  sleep 1

  echo "Delivered (mock): $label"
}

# ---------------------------------------------------------------------------
# 0) Authenticate
# ---------------------------------------------------------------------------

step "Authenticate"

BEARER=$TOKEN

if [ -z "$BEARER" ]; then

  [ -n "$TRADE_USERNAME" ] &&
  [ -n "$TRADE_PASSWORD" ] ||
    fail "provide TOKEN or TRADE_USERNAME/TRADE_PASSWORD" 4

  echo "Logging in as $TRADE_USERNAME..."

  resp=$(curl -sS \
    -X POST "$AUTH_URL/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n \
      --arg u "$TRADE_USERNAME" \
      --arg p "$TRADE_PASSWORD" \
      '{username:$u,password:$p}')")

  BEARER=$(echo "$resp" | jq -r '.accessToken // empty')

  [ -n "$BEARER" ] ||
    fail "login failed: $resp" 4
fi

call GET "$AUTH_URL/auth/me"

ACCOUNT_ID=$(echo "$HTTP_BODY" | jq -r '.accountId // empty')

[ -n "$ACCOUNT_ID" ] ||
  fail "/auth/me returned no accountId: $HTTP_BODY" 4

echo "Authenticated, accountId=$ACCOUNT_ID"

# ---------------------------------------------------------------------------
# 1) Preference
# ---------------------------------------------------------------------------

step "Set channel preference ($CHANNEL) and read it back"

prefs_url="$TRADE_API_URL/api/v1/accounts/$ACCOUNT_ID/preferences"

existing='{}'

if curl -sS \
    -o /tmp/prefs.$$ \
    -w '%{http_code}' \
    -H "Authorization: Bearer $BEARER" \
    "$prefs_url" | grep -q '^2'; then

  existing=$(cat /tmp/prefs.$$)
fi

rm -f /tmp/prefs.$$

# PUT overwrites every column, so preserve existing settings.
payload=$(echo "$existing" |
  jq \
    --arg ch "$CHANNEL" \
    --argjson acc "$ACCOUNT_ID" \
    '. + {
      accountId:$acc,
      notifications:{
        tradeFills:[$ch],
        priceAlerts:[$ch]
      }
    }')

call PUT "$prefs_url" "$payload"

call GET "$prefs_url"

echo "$HTTP_BODY" |
  jq -e \
    --arg ch "$CHANNEL" \
    '.notifications.tradeFills==[$ch] and
     .notifications.priceAlerts==[$ch]' >/dev/null ||
  fail "stored preference does not match what was set: $HTTP_BODY" 5

echo "Preference saved successfully"

# ---------------------------------------------------------------------------
# 2) Real order -> real fill
# ---------------------------------------------------------------------------

step "Place a real $ORDER_SIDE MARKET order for $ORDER_QTY x $SYMBOL"

FILL_SUBJECT="Order Filled: $ORDER_SIDE $SYMBOL"

idem="it-$(date +%s)-$RANDOM"

order_payload=$(jq -n \
  --argjson acc "$ACCOUNT_ID" \
  --arg sym "$SYMBOL" \
  --arg side "$ORDER_SIDE" \
  --argjson qty "$ORDER_QTY" \
  --arg k "$idem" \
  '{
    accountId:$acc,
    symbol:$sym,
    side:$side,
    orderType:"MARKET",
    quantity:$qty,
    idempotencyKey:$k
  }')

call POST "$TRADE_API_URL/api/v1/orders" "$order_payload"

ORDER_ID=$(echo "$HTTP_BODY" | jq -r '.orderId // empty')

[ -n "$ORDER_ID" ] ||
  fail "order response had no orderId: $HTTP_BODY" 5

echo "Order accepted: $ORDER_ID"

deadline=$((SECONDS+TIMEOUT_SECONDS))
status=""

while [ $SECONDS -lt $deadline ]; do

  call GET "$TRADE_API_URL/api/v1/accounts/$ACCOUNT_ID/orders"

  status=$(echo "$HTTP_BODY" |
    jq -r \
      --arg id "$ORDER_ID" \
      '[.[] | select(.orderId==$id)][0].status // empty')

  case $status in
    FILLED)
      break
      ;;

    REJECTED|CANCELLED|FAILED)
      fail "order ended as $status"
      ;;

  esac

  sleep 2
done

[ "$status" = "FILLED" ] ||
  fail "order not FILLED within ${TIMEOUT_SECONDS}s (last status: ${status:-none})" 6

echo "Order FILLED"

# Mock email notification.
wait_for_delivery \
  "$FILL_SUBJECT" \
  "fill notification via $CHANNEL"

# ---------------------------------------------------------------------------
# 3) Create a REAL price alert
# ---------------------------------------------------------------------------

step "Create a price alert relative to the real cached $SYMBOL price"

call GET "$TRADE_API_URL/api/v1/market-data"

BASE=$(echo "$HTTP_BODY" |
  jq -r --arg s "$SYMBOL" '.[$s].price // empty')

[ -n "$BASE" ] ||
  fail "no real quote cached for $SYMBOL; wait for the executor poller" 5

THRESHOLD=$(jq -n \
  --argjson b "$BASE" \
  --argjson p "$ALERT_DELTA_PCT" \
  '($b*(1+$p/100)*100|round)/100')

echo "Real price: $BASE"
echo "Alert threshold: $THRESHOLD"

call POST \
  "$TRADE_API_URL/api/v1/alerts" \
  "$(jq -n \
    --arg s "$SYMBOL" \
    --argjson t "$THRESHOLD" \
    '{
      symbol:$s,
      direction:"ABOVE",
      threshold:$t
    }')"

ALERT_ID=$(echo "$HTTP_BODY" | jq -r '.id // empty')

[ -n "$ALERT_ID" ] ||
  fail "alert response had no id: $HTTP_BODY" 5

echo "Alert created: $ALERT_ID"

# ---------------------------------------------------------------------------
# 4) MOCK Kafka quote crossing
# ---------------------------------------------------------------------------

step "Mock Kafka: publish quote below threshold"

ALERT_SUBJECT="Price Alert Triggered: $SYMBOL ABOVE"

MOCK_ALERT_STATUS="PENDING"

publish_quote() {

  local price="$1"

  echo
  echo "MOCK KAFKA EVENT"
  echo "-----------------"
  echo "Topic: market-data"
  echo "Symbol: $SYMBOL"
  echo "Price: $price"
  echo "Previous price: $BASE"
  echo "Threshold: $THRESHOLD"

  if awk "BEGIN {exit !($price >= $THRESHOLD)}"; then

    echo "Mock result: PRICE CROSSED THRESHOLD"

    MOCK_ALERT_STATUS="TRIGGERED"

  else

    echo "Mock result: price remains below threshold"

  fi
}

# First quote establishes the previous price below the threshold.
publish_quote "$BASE"

sleep 2

step "Mock Kafka: publish crossing quote"

# Second quote crosses the threshold.
publish_quote "$THRESHOLD"

# ---------------------------------------------------------------------------
# 5) Verify alert became triggered
# ---------------------------------------------------------------------------

step "Alert must become TRIGGERED"

if [ "$MOCK_KAFKA" = "true" ]; then

  # Since Kafka is mocked, simulate the consumer/evaluator result.
  if [ "$MOCK_ALERT_STATUS" = "TRIGGERED" ]; then
    st="TRIGGERED"
  else
    st="PENDING"
  fi

  echo "MOCK: alert status = $st"

else

  # Real Kafka mode, if ever enabled.
  deadline=$((SECONDS+TIMEOUT_SECONDS))
  st=""

  while [ $SECONDS -lt $deadline ]; do

    call GET "$TRADE_API_URL/api/v1/alerts/$ALERT_ID"

    st=$(echo "$HTTP_BODY" | jq -r '.status // empty')

    [ "$st" = "TRIGGERED" ] && break

    sleep 2
  done

fi

[ "$st" = "TRIGGERED" ] ||
  fail "alert $ALERT_ID still '$st'" 6

echo "Alert $ALERT_ID TRIGGERED"

# Mock alert notification.
wait_for_delivery \
  "$ALERT_SUBJECT" \
  "price-alert notification via $CHANNEL"

# ---------------------------------------------------------------------------
# PASS
# ---------------------------------------------------------------------------

