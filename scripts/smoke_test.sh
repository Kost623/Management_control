#!/usr/bin/env bash
# Наскрізна перевірка API Management Control.
# Використання: ./scripts/smoke_test.sh [http://localhost:8080]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
ADMIN_USER="${MC_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${MC_ADMIN_PASSWORD:-admin123}"

command -v jq >/dev/null || { echo "Потрібен jq"; exit 1; }

step() { printf '\n=== %s ===\n' "$1"; }

step "1. Вхід адміністратора"
LOGIN=$(curl -fsS -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN" | jq -r '.token')
echo "отримано JWT: ${TOKEN:0:24}…"

step "2. Створення коду реєстрації"
CODE=$(curl -fsS -X POST "$BASE/api/v1/admin/enrollment-codes" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"label":"smoke-test","assignedTo":"manager-smoke"}' | jq -r '.code')
echo "код реєстрації: $CODE"

step "3. Реєстрація пристрою агентом"
ENROLL=$(curl -fsS -X POST "$BASE/api/v1/agent/enroll" \
  -H 'Content-Type: application/json' \
  -d "{\"code\":\"$CODE\",\"deviceId\":\"smoke-device-1\",\"hostname\":\"smoke-pc\",\
\"osName\":\"Windows 11\",\"osVersion\":\"11.0\",\"agentVersion\":\"0.1.0\",\
\"macAddress\":\"00:11:22:33:44:55\",\"ipAddress\":\"127.0.0.1\",\"osUser\":\"manager\"}")
DEVICE_TOKEN=$(echo "$ENROLL" | jq -r '.token')
DEVICE_ID=$(echo "$ENROLL" | jq -r '.deviceDbId')
echo "device id=$DEVICE_ID, токен: ${DEVICE_TOKEN:0:16}…"

step "4. Heartbeat"
curl -fsS -X POST "$BASE/api/v1/agent/heartbeat" \
  -H "Authorization: Bearer $DEVICE_TOKEN" -H 'Content-Type: application/json' \
  -d '{"agentVersion":"0.1.0","ipAddress":"127.0.0.1","batteryPercent":88,"charging":false}' | jq -c .

step "5. Передача часу використання програм"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -fsS -X POST "$BASE/api/v1/agent/usage-sessions" \
  -H "Authorization: Bearer $DEVICE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"sessions\":[{\"processName\":\"chrome.exe\",\"windowTitle\":\"Приклад\",\
\"exePath\":\"C:\\\\chrome.exe\",\"startedAt\":\"$NOW\",\"endedAt\":\"$NOW\",\
\"durationSeconds\":120,\"idle\":false,\"foreground\":true}]}" | jq -c .

step "6. Передача відвідувань сайтів"
curl -fsS -X POST "$BASE/api/v1/agent/browsing" \
  -H "Authorization: Bearer $DEVICE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"activities\":[{\"url\":\"https://example.com\",\"domain\":\"example.com\",\
\"browserName\":\"chrome\",\"tabTitle\":\"Example\",\"startedAt\":\"$NOW\",\
\"endedAt\":\"$NOW\",\"durationSeconds\":90,\"idle\":false}]}" | jq -c .

step "7. Передача локації"
curl -fsS -X POST "$BASE/api/v1/agent/location" \
  -H "Authorization: Bearer $DEVICE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"latitude\":50.45,\"longitude\":30.52,\"accuracy\":25.0,\"capturedAt\":\"$NOW\",\"source\":\"GPS\"}" | jq -c .

step "8. Завантаження скриншота"
SHOT=/tmp/mc_shot.png
if command -v import >/dev/null 2>&1 && import -window root "$SHOT" 2>/dev/null && [ -s "$SHOT" ]; then
  :
else
  printf '\x89PNG\r\n\x1a\n' > "$SHOT"
fi
[ -s "$SHOT" ] || { echo "не вдалося підготувати файл скриншота"; exit 1; }
curl -fsS -X POST "$BASE/api/v1/agent/screenshots" \
  -H "Authorization: Bearer $DEVICE_TOKEN" \
  -F "file=@$SHOT" -F "takenAt=$NOW" -F "trigger=AUTO_RANDOM" \
  -F "width=1920" -F "height=1080" | jq -c '{id, fileName, sizeBytes, takenAt}'

step "9. Команда LOCK і long-poll"
CMD=$(curl -fsS -X POST "$BASE/api/v1/admin/devices/$DEVICE_ID/commands" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"LOCK","payload":"тест"}' | jq -r '.id')
echo "команда #$CMD створена"
curl -fsS -m 40 "$BASE/api/v1/agent/commands?timeoutSeconds=10" \
  -H "Authorization: Bearer $DEVICE_TOKEN" | jq -c .

step "10. Підтвердження виконання"
curl -fsS -X POST "$BASE/api/v1/agent/commands/$CMD/ack" \
  -H "Authorization: Bearer $DEVICE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"commandId\":$CMD,\"status\":\"EXECUTED\",\"result\":\"ok\"}" -o /dev/null -w 'HTTP %{http_code}\n'

step "11. Звіт за добу"
curl -fsS "$BASE/api/v1/admin/reports/daily?deviceId=$DEVICE_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .

step "12. Список пристроїв"
curl -fsS "$BASE/api/v1/admin/devices" -H "Authorization: Bearer $TOKEN" | jq '.content[] | {id, deviceId, hostname, status, lastSeenAt}'

printf '\n✅ Усі кроки пройшли\n'
