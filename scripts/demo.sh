#!/usr/bin/env bash
set -euo pipefail

json_field() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

wait_for_health() {
  local url="$1"
  local name="$2"
  for _ in $(seq 1 60); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Timeout esperando $name" >&2
  exit 1
}

wait_for_health "http://localhost:8081/actuator/health" "mechanic-service"
wait_for_health "http://localhost:8082/actuator/health" "assistance-service"
wait_for_health "http://localhost:8083/actuator/health" "dispatch-service"

run_id=$(date +%s)

mechanic_one=$(curl -fsS -X POST http://localhost:8081/api/mechanics \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Ada Mecánica\",\"email\":\"ada-${run_id}@example.com\",\"latitude\":-34.6038,\"longitude\":-58.3820,\"skills\":[\"CHAIN_REPAIR\",\"TIRE_REPAIR\"],\"vehicleTypes\":[\"BICYCLE\",\"E_BIKE\"]}")
mechanic_one_id=$(printf '%s' "$mechanic_one" | json_field id)

mechanic_two=$(curl -fsS -X POST http://localhost:8081/api/mechanics \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Linus Bicis\",\"email\":\"linus-${run_id}@example.com\",\"latitude\":-34.6400,\"longitude\":-58.4400,\"skills\":[\"BRAKE_REPAIR\"],\"vehicleTypes\":[\"MOTORCYCLE\"]}")
mechanic_two_id=$(printf '%s' "$mechanic_two" | json_field id)

curl -fsS -X PATCH "http://localhost:8081/api/mechanics/${mechanic_one_id}/availability" \
  -H 'Content-Type: application/json' \
  -d '{"available":true,"latitude":-34.6038,"longitude":-58.3820}' >/dev/null

curl -fsS -X PATCH "http://localhost:8081/api/mechanics/${mechanic_two_id}/availability" \
  -H 'Content-Type: application/json' \
  -d '{"available":true,"latitude":-34.6400,"longitude":-58.4400}' >/dev/null

client=$(curl -fsS -X POST http://localhost:8082/api/clients \
  -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Repartidor Demo\",\"phone\":\"1155550000\",\"email\":\"repartidor-${run_id}@example.com\"}")
client_id=$(printf '%s' "$client" | json_field id)

vehicle=$(curl -fsS -X POST "http://localhost:8082/api/clients/${client_id}/vehicles" \
  -H 'Content-Type: application/json' \
  -d '{"type":"BICYCLE","brand":"Trek","model":"FX 2","color":"Negra"}')
vehicle_id=$(printf '%s' "$vehicle" | json_field id)

assistance=$(curl -fsS -X POST http://localhost:8082/api/assistances \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-mecanicosya' \
  -d "{\"clientId\":\"${client_id}\",\"vehicleId\":\"${vehicle_id}\",\"description\":\"Se cortó la cadena en el centro\",\"problemType\":\"CHAIN\",\"requiredSkill\":\"CHAIN_REPAIR\",\"latitude\":-34.6037,\"longitude\":-58.3816}")
assistance_id=$(printf '%s' "$assistance" | json_field id)

for _ in $(seq 1 20); do
  current=$(curl -fsS "http://localhost:8082/api/assistances/${assistance_id}")
  status=$(printf '%s' "$current" | json_field status)
  if [ "$status" = "MATCHED" ]; then
    break
  fi
  sleep 1
done

echo "Asistencia:"
curl -fsS "http://localhost:8082/api/assistances/${assistance_id}"
echo
echo "Despacho:"
curl -fsS "http://localhost:8083/api/dispatches/${assistance_id}"
echo
echo "Historial de estados:"
curl -fsS "http://localhost:8082/api/assistances/${assistance_id}/history"
echo
