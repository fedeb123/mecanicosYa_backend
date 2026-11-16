#!/usr/bin/env bash
set -euo pipefail

# Credenciales del admin que crea identity-service al arrancar (las mismas que usa docker-compose.yml).
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@mecanicosya.local}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-admin-demo-123}

IDENTITY=http://localhost:8084
MECHANICS=http://localhost:8081
ASSISTANCES=http://localhost:8082
DISPATCH=http://localhost:8083

json_field() {
  python3 -c 'import json,sys; v=json.load(sys.stdin)[sys.argv[1]]; print("" if v is None else v)' "$1"
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

# curl con el access token de quien hace el pedido: api TOKEN METODO URL [BODY]
api() {
  local token="$1" method="$2" url="$3" body="${4:-}"
  if [ -n "$body" ]; then
    curl -fsS -X "$method" "$url" -H "Authorization: Bearer ${token}" -H 'Content-Type: application/json' -d "$body"
  else
    curl -fsS -X "$method" "$url" -H "Authorization: Bearer ${token}"
  fi
}

# Espera hasta que el campo del JSON que devuelve la URL tenga un valor (distinto de null y de vacío).
wait_for_field() {
  local token="$1" url="$2" field="$3" value
  for _ in $(seq 1 30); do
    value=$(api "$token" GET "$url" | python3 -c 'import json,sys; v=json.load(sys.stdin).get(sys.argv[1]); print("" if v in (None, 0) else v)' "$field")
    if [ -n "$value" ]; then
      return 0
    fi
    sleep 0.5
  done
  echo "Timeout esperando ${field} en ${url}" >&2
  exit 1
}

# Alta en identity-service; devuelve la sesión (accessToken, refreshToken, userId, role, verificationStatus).
sign_up() {
  curl -fsS -X POST "${IDENTITY}/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"demo-pass-123\",\"role\":\"$2\"}"
}

# Renueva la sesión hasta que el token diga APPROVED: la aprobación llega a identity-service por Kafka.
refresh_until_approved() {
  local refresh="$1" session status
  for _ in $(seq 1 30); do
    session=$(curl -fsS -X POST "${IDENTITY}/api/auth/refresh" -H 'Content-Type: application/json' \
      -d "{\"refreshToken\":\"${refresh}\"}")
    status=$(printf '%s' "$session" | json_field verificationStatus)
    refresh=$(printf '%s' "$session" | json_field refreshToken)
    if [ "$status" = "APPROVED" ]; then
      printf '%s' "$session"
      return 0
    fi
    sleep 0.5
  done
  echo "Timeout esperando la aprobación en identity-service" >&2
  exit 1
}

wait_for_health "${IDENTITY}/actuator/health" "identity-service"
wait_for_health "${MECHANICS}/actuator/health" "mechanic-service"
wait_for_health "${ASSISTANCES}/actuator/health" "assistance-service"
wait_for_health "${DISPATCH}/actuator/health" "dispatch-service"

run_id=$(date +%s)

echo "A. Usuarios: tres mecánicos y un repartidor se registran; el admin inicia sesión."
admin_token=$(curl -fsS -X POST "${IDENTITY}/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" | json_field accessToken)

declare -A token refresh id
for name in ada linus grace; do
  session=$(sign_up "${name}-${run_id}@example.com" MECHANIC)
  token[$name]=$(printf '%s' "$session" | json_field accessToken)
  refresh[$name]=$(printf '%s' "$session" | json_field refreshToken)
  id[$name]=$(printf '%s' "$session" | json_field userId)
done

# El perfil se crea con el id y el email del token.
api "${token[ada]}" POST "${MECHANICS}/api/mechanics" \
  '{"name":"Ada Mecánica","phone":"11 5555 0101","latitude":-34.5800,"longitude":-58.4255,"skills":["CHAIN_REPAIR","TIRE_REPAIR"],"vehicleTypes":["BICYCLE","E_BIKE"]}' >/dev/null
api "${token[linus]}" POST "${MECHANICS}/api/mechanics" \
  '{"name":"Linus Motos","phone":"11 5555 0102","latitude":-34.6400,"longitude":-58.4400,"skills":["BRAKE_REPAIR"],"vehicleTypes":["MOTORCYCLE"]}' >/dev/null
api "${token[grace]}" POST "${MECHANICS}/api/mechanics" \
  '{"name":"Grace Rodados","phone":"11 5555 0103","latitude":-34.5840,"longitude":-58.4300,"skills":["CHAIN_REPAIR"],"vehicleTypes":["BICYCLE"]}' >/dev/null

echo "   Ada todavía no fue aprobada: ponerse disponible le da"
curl -sS -o /dev/null -w '   HTTP %{http_code}\n' -X PATCH "${MECHANICS}/api/mechanics/${id[ada]}/availability" \
  -H "Authorization: Bearer ${token[ada]}" -H 'Content-Type: application/json' \
  -d '{"available":true,"latitude":-34.5800,"longitude":-58.4255}'

echo "   El admin aprueba a los tres; cada uno renueva su token, que ahora dice APPROVED."
for name in ada linus grace; do
  api "$admin_token" POST "${MECHANICS}/api/mechanics/${id[$name]}/approve" >/dev/null
  session=$(refresh_until_approved "${refresh[$name]}")
  token[$name]=$(printf '%s' "$session" | json_field accessToken)
done

api "${token[ada]}" PATCH "${MECHANICS}/api/mechanics/${id[ada]}/availability" \
  '{"available":true,"latitude":-34.5800,"longitude":-58.4255}' >/dev/null
api "${token[linus]}" PATCH "${MECHANICS}/api/mechanics/${id[linus]}/availability" \
  '{"available":true,"latitude":-34.6400,"longitude":-58.4400}' >/dev/null
api "${token[grace]}" PATCH "${MECHANICS}/api/mechanics/${id[grace]}/availability" \
  '{"available":true,"latitude":-34.5840,"longitude":-58.4300}' >/dev/null

rider_session=$(sign_up "repartidor-${run_id}@example.com" RIDER)
rider_token=$(printf '%s' "$rider_session" | json_field accessToken)
client_id=$(printf '%s' "$rider_session" | json_field userId)
api "$rider_token" POST "${ASSISTANCES}/api/clients" '{"fullName":"Repartidor Demo","phone":"1155550000"}' >/dev/null
vehicle_id=$(api "$rider_token" POST "${ASSISTANCES}/api/clients/${client_id}/vehicles" \
  '{"type":"BICYCLE","brand":"Trek","model":"FX 2","color":"Negra"}' | json_field id)
echo

# La app genera una clave por pedido y la repite en cada reintento.
idempotency_key="demo-${run_id}"
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
# El repartidor sólo describe el problema: la clasificación completa problemType, requiredSkill y la urgencia.
# El cliente sale del token, así que el cuerpo no lleva clientId.
assistance_body="{\"vehicleId\":\"${vehicle_id}\",\"description\":\"Se cortó la cadena en Palermo\",\"latitude\":-34.5795,\"longitude\":-58.4250,\"clientCreatedAt\":\"${created_at}\"}"

echo "0. Clasificación del texto (IA si hay ANTHROPIC_API_KEY, si no palabras clave):"
api "$rider_token" POST "${ASSISTANCES}/api/triage" '{"description":"Se cortó la cadena en Palermo","vehicleType":"BICYCLE"}'
echo
echo

echo "1. Pedido de asistencia sin tipo de problema (Idempotency-Key ${idempotency_key}):"
assistance=$(curl -fsS -X POST "${ASSISTANCES}/api/assistances" \
  -H "Authorization: Bearer ${rider_token}" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-mecanicosya' \
  -H "Idempotency-Key: ${idempotency_key}" \
  -d "$assistance_body")
assistance_id=$(printf '%s' "$assistance" | json_field id)
echo "$assistance"
echo

echo "2. Reintento con la misma clave (no crea otro pedido):"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -X POST "${ASSISTANCES}/api/assistances" \
  -H "Authorization: Bearer ${rider_token}" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${idempotency_key}" \
  -d "$assistance_body"
echo

echo "3. dispatch-service toma el pedido de Kafka (assistance.events) y lo ofrece; assistance-service recibe la"
echo "   oferta por dispatch.events y guarda hasta cuándo se puede aceptar:"
wait_for_field "$rider_token" "${ASSISTANCES}/api/assistances/${assistance_id}" offersExpireAt
api "$rider_token" GET "${ASSISTANCES}/api/assistances/${assistance_id}"
echo
echo
echo "   Ofertas abiertas de Ada y de Grace, cada una con su token (Linus arregla motos, no recibe el pedido):"
api "${token[ada]}" GET "${DISPATCH}/api/dispatches/offers"
echo
api "${token[grace]}" GET "${DISPATCH}/api/dispatches/offers"
echo
echo

echo "4. Ada y Grace aceptan al mismo tiempo (el mecánico sale del token):"
tmp=$(mktemp -d)
for name in ada grace; do
  curl -sS -o "${tmp}/${name}.json" -w '%{http_code}' -X POST \
    "${DISPATCH}/api/dispatches/${assistance_id}/accept" \
    -H "Authorization: Bearer ${token[$name]}" > "${tmp}/${name}.code" &
done
wait
for name in ada grace; do
  echo "${name}: HTTP $(cat "${tmp}/${name}.code") $(cat "${tmp}/${name}.json")"
done
rm -rf "$tmp"
echo

echo "Asistencia:"
api "$rider_token" GET "${ASSISTANCES}/api/assistances/${assistance_id}"
echo
echo "Despacho (lo ve el admin):"
api "$admin_token" GET "${DISPATCH}/api/dispatches/${assistance_id}"
echo

assigned_mechanic_id=$(api "$rider_token" GET "${ASSISTANCES}/api/assistances/${assistance_id}" | json_field assignedMechanicId)
for name in ada grace; do
  if [ "${id[$name]}" = "$assigned_mechanic_id" ]; then
    assigned=$name
  fi
done
if [ "$assigned" = ada ]; then other=grace; else other=ada; fi
echo "5. ${assigned} está en un trabajo: llega otro pedido y sólo se le ofrece a ${other}."
second_assistance_id=$(curl -fsS -X POST "${ASSISTANCES}/api/assistances" \
  -H "Authorization: Bearer ${rider_token}" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: ${idempotency_key}-2" \
  -d "$assistance_body" | json_field id)
wait_for_field "$rider_token" "${ASSISTANCES}/api/assistances/${second_assistance_id}" offersExpireAt
echo "   Ofertas abiertas de ${assigned}, que está trabajando:"
api "${token[$assigned]}" GET "${DISPATCH}/api/dispatches/offers"
echo
echo "   Ofertas abiertas de ${other}:"
api "${token[$other]}" GET "${DISPATCH}/api/dispatches/offers"
echo
api "$rider_token" POST "${ASSISTANCES}/api/assistances/${second_assistance_id}/cancel" >/dev/null
echo "   (el repartidor cancela ese segundo pedido)"
echo

echo "El repartidor no puede marcar el trabajo como empezado; ${assigned} sí:"
curl -sS -o /dev/null -w 'repartidor: HTTP %{http_code}\n' -X POST \
  "${ASSISTANCES}/api/assistances/${assistance_id}/start" -H "Authorization: Bearer ${rider_token}"
api "${token[$assigned]}" POST "${ASSISTANCES}/api/assistances/${assistance_id}/start" >/dev/null
api "${token[$assigned]}" POST "${ASSISTANCES}/api/assistances/${assistance_id}/complete" >/dev/null
echo
echo "Calificación del trabajo:"
api "$rider_token" POST "${ASSISTANCES}/api/assistances/${assistance_id}/rating" \
  '{"score":4,"comment":"Llegó rápido y dejó la bici andando"}'
echo
# La calificación llega a mechanic-service por Kafka.
wait_for_field "$rider_token" "${MECHANICS}/api/mechanics/${assigned_mechanic_id}" ratingCount
echo "Mecánico con su calificación actualizada (llegó por Kafka):"
api "$rider_token" GET "${MECHANICS}/api/mechanics/${assigned_mechanic_id}"
echo
echo "Historial de estados:"
api "$rider_token" GET "${ASSISTANCES}/api/assistances/${assistance_id}/history"
echo

# Los mecánicos de la demo se desconectan para no recibir los pedidos de la próxima corrida.
for name in ada linus grace; do
  api "${token[$name]}" PATCH "${MECHANICS}/api/mechanics/${id[$name]}/availability" \
    '{"available":false,"latitude":-34.5795,"longitude":-58.4250}' >/dev/null
done
