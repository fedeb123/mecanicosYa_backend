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
  -d "{\"name\":\"Ada Mecánica\",\"email\":\"ada-${run_id}@example.com\",\"latitude\":-34.5800,\"longitude\":-58.4255,\"skills\":[\"CHAIN_REPAIR\",\"TIRE_REPAIR\"],\"vehicleTypes\":[\"BICYCLE\",\"E_BIKE\"]}")
mechanic_one_id=$(printf '%s' "$mechanic_one" | json_field id)

mechanic_two=$(curl -fsS -X POST http://localhost:8081/api/mechanics \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Linus Motos\",\"email\":\"linus-${run_id}@example.com\",\"latitude\":-34.6400,\"longitude\":-58.4400,\"skills\":[\"BRAKE_REPAIR\"],\"vehicleTypes\":[\"MOTORCYCLE\"]}")
mechanic_two_id=$(printf '%s' "$mechanic_two" | json_field id)

mechanic_three=$(curl -fsS -X POST http://localhost:8081/api/mechanics \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Grace Rodados\",\"email\":\"grace-${run_id}@example.com\",\"latitude\":-34.5840,\"longitude\":-58.4300,\"skills\":[\"CHAIN_REPAIR\"],\"vehicleTypes\":[\"BICYCLE\"]}")
mechanic_three_id=$(printf '%s' "$mechanic_three" | json_field id)

curl -fsS -X PATCH "http://localhost:8081/api/mechanics/${mechanic_one_id}/availability" \
  -H 'Content-Type: application/json' \
  -d '{"available":true,"latitude":-34.5800,"longitude":-58.4255}' >/dev/null

curl -fsS -X PATCH "http://localhost:8081/api/mechanics/${mechanic_two_id}/availability" \
  -H 'Content-Type: application/json' \
  -d '{"available":true,"latitude":-34.6400,"longitude":-58.4400}' >/dev/null

curl -fsS -X PATCH "http://localhost:8081/api/mechanics/${mechanic_three_id}/availability" \
  -H 'Content-Type: application/json' \
  -d '{"available":true,"latitude":-34.5840,"longitude":-58.4300}' >/dev/null

client=$(curl -fsS -X POST http://localhost:8082/api/clients \
  -H 'Content-Type: application/json' \
  -d "{\"fullName\":\"Repartidor Demo\",\"phone\":\"1155550000\",\"email\":\"repartidor-${run_id}@example.com\"}")
client_id=$(printf '%s' "$client" | json_field id)

vehicle=$(curl -fsS -X POST "http://localhost:8082/api/clients/${client_id}/vehicles" \
  -H 'Content-Type: application/json' \
  -d '{"type":"BICYCLE","brand":"Trek","model":"FX 2","color":"Negra"}')
vehicle_id=$(printf '%s' "$vehicle" | json_field id)

# La app genera una clave por pedido y la repite en cada reintento.
idempotency_key="demo-${run_id}"
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
assistance_body="{\"clientId\":\"${client_id}\",\"vehicleId\":\"${vehicle_id}\",\"description\":\"Se cortó la cadena en Palermo\",\"problemType\":\"CHAIN\",\"requiredSkill\":\"CHAIN_REPAIR\",\"latitude\":-34.5795,\"longitude\":-58.4250,\"clientCreatedAt\":\"${created_at}\"}"

echo "1. Pedido de asistencia (Idempotency-Key ${idempotency_key}):"
assistance=$(curl -fsS -X POST http://localhost:8082/api/assistances \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-mecanicosya' \
  -H "Idempotency-Key: ${idempotency_key}" \
  -d "$assistance_body")
assistance_id=$(printf '%s' "$assistance" | json_field id)
echo "$assistance"
echo

echo "2. Reintento con la misma clave (no crea otro pedido):"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -X POST http://localhost:8082/api/assistances \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${idempotency_key}" \
  -d "$assistance_body"
echo

echo "3. Ofertas abiertas de Ada y de Grace (Linus arregla motos, no recibe el pedido):"
curl -fsS "http://localhost:8083/api/dispatches/offers?mechanicId=${mechanic_one_id}"
echo
curl -fsS "http://localhost:8083/api/dispatches/offers?mechanicId=${mechanic_three_id}"
echo
echo

echo "4. Ada y Grace aceptan al mismo tiempo:"
tmp=$(mktemp -d)
for mechanic in "ada:${mechanic_one_id}" "grace:${mechanic_three_id}"; do
  name=${mechanic%%:*}
  id=${mechanic#*:}
  curl -sS -o "${tmp}/${name}.json" -w '%{http_code}' -X POST \
    "http://localhost:8083/api/dispatches/${assistance_id}/accept" \
    -H 'Content-Type: application/json' \
    -d "{\"mechanicId\":\"${id}\"}" > "${tmp}/${name}.code" &
done
wait
for name in ada grace; do
  echo "${name}: HTTP $(cat "${tmp}/${name}.code") $(cat "${tmp}/${name}.json")"
done
rm -rf "$tmp"
echo

echo "Asistencia:"
curl -fsS "http://localhost:8082/api/assistances/${assistance_id}"
echo
echo "Despacho:"
curl -fsS "http://localhost:8083/api/dispatches/${assistance_id}"
echo
echo "Historial de estados:"
curl -fsS "http://localhost:8082/api/assistances/${assistance_id}/history"
echo

# Los mecánicos de la demo se desconectan para no recibir los pedidos de la próxima corrida.
for id in "$mechanic_one_id" "$mechanic_two_id" "$mechanic_three_id"; do
  curl -fsS -X PATCH "http://localhost:8081/api/mechanics/${id}/availability" \
    -H 'Content-Type: application/json' \
    -d '{"available":false,"latitude":-34.5795,"longitude":-58.4250}' >/dev/null
done
