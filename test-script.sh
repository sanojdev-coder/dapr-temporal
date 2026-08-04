#!/bin/bash
# Sends the 3 test requests called for in the assignment's Definition of Done:
# 1 safe ($5K), 2 fraudulent ($15K, $25K). Requires curl and jq.

set -e
BASE_URL="http://localhost:8080/v1/payments"

send() {
  local id=$1
  local amount=$2
  echo ""
  echo "=== $id : amount=$amount ==="
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -d "{\"transaction_id\":\"$id\",\"amount\":$amount,\"currency\":\"USD\",\"from_account\":\"acc-123\",\"to_account\":\"acc-456\"}" \

}

send "txn-001" 5000.00    # safe
send "txn-002" 15000.00   # fraudulent
send "txn-003" 25000.00   # fraudulent

echo ""
echo "Check the audit trail:"
echo "  docker compose logs notification-service"
echo ""
echo "Check workflow execution history in the Temporal UI:"
echo "  http://localhost:8233"
