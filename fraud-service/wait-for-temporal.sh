#!/bin/bash
# Waits for TEMPORAL_SERVICE_ADDRESS (host:port) to accept a TCP connection
# before exec-ing the real command. Guards against the container starting
# before Temporal's frontend is actually listening -- depends_on only
# guarantees the *container* has started, not that the service inside it
# is ready.
set -e

ADDRESS="${TEMPORAL_SERVICE_ADDRESS:-temporal:7233}"
HOST="${ADDRESS%%:*}"
PORT="${ADDRESS##*:}"
MAX_ATTEMPTS=60

echo "Waiting for Temporal at ${HOST}:${PORT}..."
for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  if (exec 3<>"/dev/tcp/${HOST}/${PORT}") 2>/dev/null; then
    echo "Temporal is accepting connections. Starting application."
    exec "$@"
  fi
  sleep 2
done

echo "Timed out after $((MAX_ATTEMPTS * 2))s waiting for Temporal at ${HOST}:${PORT}." >&2
exit 1
