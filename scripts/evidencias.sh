#!/usr/bin/env bash
# Genera las evidencias de ejecución local y remota en docs/evidencias/.
# Uso (desde la raíz del repo, con Docker Desktop corriendo):  ./scripts/evidencias.sh
set -euo pipefail

cd "$(dirname "$0")/.."
EV=docs/evidencias
mkdir -p "$EV/capturas"
DC=(docker compose -f docker-compose.yml -f scripts/compose.evidencias.yml)
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
RUN_ID=$(date +%s)
M=http://localhost:8081
A=http://localhost:8082
D=http://localhost:8083
CID=""
LAST=""

header() {
  echo "# $1"
  echo "# $(date '+%Y-%m-%d %H:%M:%S %z') · commit $(git rev-parse --short HEAD)"
  echo
}

# req MÉTODO URL [BODY] — muestra el comando, el código HTTP y el JSON de respuesta.
req() {
  local method=$1 url=$2 body=${3:-}
  local args=(-s -w '\n%{http_code} · %{time_total}s' -X "$method" "$url" -H "X-Correlation-Id: $CID")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
    echo "\$ curl -X $method $url -H 'X-Correlation-Id: $CID' -H 'Content-Type: application/json' -d '$body'"
  else
    echo "\$ curl -X $method $url -H 'X-Correlation-Id: $CID'"
  fi
  local resp code
  resp=$(curl "${args[@]}")
  code=$(tail -n1 <<<"$resp" | sed -E 's/([0-9]+\.[0-9]{2})[0-9]*s$/\1s/')
  LAST=$(sed '$d' <<<"$resp")
  echo "HTTP $code"
  if [ -n "$LAST" ]; then jq . <<<"$LAST" 2>/dev/null || echo "$LAST"; fi
  echo
}

field() { jq -r ".$1" <<<"$LAST"; }

wait_healthy() {
  for _ in $(seq 1 90); do
    if curl -fsS "$1/actuator/health" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "Timeout esperando $1" >&2
  exit 1
}

shot() { # shot URL ARCHIVO ANCHO ALTO
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size="$3,$4" \
    --virtual-time-budget=15000 --screenshot="$2" "$1" >/dev/null 2>&1 || true
}

new_mechanic() { # nombre lat lon skill
  CID="ev-${RUN_ID}"
  req POST "$M/api/mechanics" "{\"name\":\"$1\",\"email\":\"$(tr 'A-Z ' 'a-z-' <<<"$1")-${RUN_ID}@example.com\",\"latitude\":$2,\"longitude\":$3,\"skills\":[\"$4\"],\"vehicleTypes\":[\"BICYCLE\"]}" >/dev/null
  local id; id=$(field id)
  req PATCH "$M/api/mechanics/$id/availability" "{\"available\":true,\"latitude\":$2,\"longitude\":$3}" >/dev/null
  echo "$id"
}

new_client_vehicle() { # sufijo -> deja CLIENT y VEHICLE
  req POST "$A/api/clients" "{\"fullName\":\"Repartidor $1\",\"phone\":\"1155550000\",\"email\":\"rep-$1-${RUN_ID}@example.com\"}" >/dev/null
  CLIENT=$(field id)
  req POST "$A/api/clients/$CLIENT/vehicles" '{"type":"BICYCLE","brand":"Trek","model":"FX 2","color":"Negra"}' >/dev/null
  VEHICLE=$(field id)
}

assistance_body() { # skill
  echo "{\"clientId\":\"$CLIENT\",\"vehicleId\":\"$VEHICLE\",\"description\":\"Pinchadura en el centro\",\"problemType\":\"TIRE\",\"requiredSkill\":\"$1\",\"latitude\":-34.6037,\"longitude\":-58.3816}"
}

echo "== Levantando el entorno"
"${DC[@]}" up -d --build --wait
wait_healthy $M; wait_healthy $A; wait_healthy $D

# ---------------------------------------------------------------- E02
{
  header "E02 · Contenedores levantados con Docker Compose"
  echo "\$ docker compose ps"
  "${DC[@]}" ps --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
  echo
  echo "\$ docker compose images"
  "${DC[@]}" images
} >"$EV/02-docker-compose-ps.txt"

# ---------------------------------------------------------------- E03
for svc in mechanic:8081 assistance:8082 dispatch:8083; do
  name=${svc%%:*}; port=${svc##*:}
  curl -fsS "http://localhost:$port/v3/api-docs" | jq . >"$EV/03-openapi-$name-service.json"
  shot "http://localhost:$port/swagger-ui/index.html" "$EV/capturas/03-swagger-$name-service.png" 1440 1600
done

# ---------------------------------------------------------------- E05
{
  header "E05 · Flujo completo con scripts/demo.sh"
  echo "\$ ./scripts/demo.sh"
  ./scripts/demo.sh | sed -e 's/^\({.*\)$/\1/' | while IFS= read -r line; do
    if jq . >/dev/null 2>&1 <<<"$line"; then jq . <<<"$line"; else echo "$line"; fi
  done
} >"$EV/05-demo-flujo-completo.txt"

# ---------------------------------------------------------------- E06 / E07
{
  header "E06 · Acceso local: fachada, repositorio (SQL) y eventos de dominio en assistance-service"
  echo "Logs del request con X-Correlation-Id demo-mecanicosya (mismo hilo y mismo proceso):"
  echo "\$ docker compose logs assistance-service | grep demo-mecanicosya"
  "${DC[@]}" logs --no-color --no-log-prefix assistance-service | grep "demo-mecanicosya" \
    | sed -E 's/ +/ /g' || true
} >"$EV/06-acceso-local-logs.txt"

{
  header "E07 · Acceso remoto: la misma petición atraviesa los tres servicios"
  echo "El correlation id viaja en el header X-Correlation-Id de cada llamada REST."
  echo "\$ docker compose logs assistance-service dispatch-service mechanic-service | grep demo-mecanicosya | grep -v 'org.hibernate.SQL'"
  "${DC[@]}" logs --no-color assistance-service dispatch-service mechanic-service \
    | grep "demo-mecanicosya" | grep -v "org.hibernate.SQL" | sort -t'|' -k2 || true
} >"$EV/07-acceso-remoto-correlation-id.txt"

# ---------------------------------------------------------------- E04
{
  header "E04 · Una base de datos por servicio (componente de acceso a datos)"
  for db in mechanics_db assistances_db dispatch_db; do
    echo "\$ docker compose exec postgres psql -U mecanicosya_admin -d $db -c '\\dt'"
    "${DC[@]}" exec -T postgres psql -U mecanicosya_admin -d "$db" -c '\dt'
  done
  echo "\$ docker compose exec postgres psql -U mecanicosya_admin -d assistances_db -c 'select from_status, to_status, changed_at from assistance_status_changes order by changed_at desc limit 6'"
  "${DC[@]}" exec -T postgres psql -U mecanicosya_admin -d assistances_db \
    -c 'select from_status, to_status, changed_at from assistance_status_changes order by changed_at desc limit 6'
} >"$EV/04-bases-por-servicio.txt"

# ---------------------------------------------------------------- E08
SKILL="STRATEGY_DEMO_${RUN_ID}"
{
  header "E08 · Patrón Strategy configurable (dispatch.matching.strategy)"
  echo "Un único mecánico con la habilidad $SKILL, a unos 8 km del pedido."
  FAR=$(new_mechanic "Mecanico Lejano" -34.5317 -58.3816 "$SKILL")
  echo "mecánico: $FAR"
  echo
  echo "## Estrategia nearest (por defecto): elige al más cercano, sin límite de distancia"
  echo "\$ docker compose exec dispatch-service printenv DISPATCH_MATCHING_STRATEGY"
  "${DC[@]}" exec -T dispatch-service printenv DISPATCH_MATCHING_STRATEGY
  echo
  CID="ev-strategy-nearest-${RUN_ID}"
  new_client_vehicle nearest
  req POST "$A/api/assistances" "$(assistance_body "$SKILL")"
  NEAREST=$(field id)
  sleep 1
  req GET "$D/api/dispatches/$NEAREST"
  echo "## Estrategia best-rated: mejor calificado dentro de 5 km"
  echo "\$ DISPATCH_MATCHING_STRATEGY=best-rated docker compose up -d --wait dispatch-service"
  DISPATCH_MATCHING_STRATEGY=best-rated "${DC[@]}" up -d --wait dispatch-service >/dev/null 2>&1
  wait_healthy $D
  echo "\$ docker compose exec dispatch-service printenv DISPATCH_MATCHING_STRATEGY"
  "${DC[@]}" exec -T dispatch-service printenv DISPATCH_MATCHING_STRATEGY
  echo
  CID="ev-strategy-best-rated-${RUN_ID}"
  new_client_vehicle bestrated
  req POST "$A/api/assistances" "$(assistance_body "$SKILL")"
  BEST=$(field id)
  sleep 1
  req GET "$D/api/dispatches/$BEST"
  req GET "$A/api/assistances/$BEST"
  echo "Con best-rated el mecánico queda fuera del radio de 5 km: NO_CANDIDATE y la asistencia sigue REQUESTED."
} >"$EV/08-strategy-nearest-vs-best-rated.txt"
"${DC[@]}" up -d --wait dispatch-service >/dev/null 2>&1
wait_healthy $D

# ---------------------------------------------------------------- E09
{
  header "E09 · Validación y manejo de errores con Problem Details"
  CID="ev-errores-${RUN_ID}"
  echo "## 400 · datos inválidos en el alta de mecánico"
  req POST "$M/api/mechanics" '{"name":"","email":"no-es-un-email","latitude":120,"longitude":-58.38,"skills":[]}'
  echo "## 400 · regla de dominio: una moto requiere patente"
  new_client_vehicle errores
  req POST "$A/api/clients/$CLIENT/vehicles" '{"type":"MOTORCYCLE","brand":"Honda","model":"Wave"}'
  echo "## 404 · asistencia inexistente"
  req GET "$A/api/assistances/00000000-0000-0000-0000-000000000000"
  echo "## 409 · transición inválida (iniciar una asistencia que sigue REQUESTED)"
  req POST "$A/api/assistances/$BEST/start"
  echo "## 409 · email de cliente repetido"
  req POST "$A/api/clients" "{\"fullName\":\"Otro\",\"phone\":\"1100000000\",\"email\":\"rep-errores-${RUN_ID}@example.com\"}"
} >"$EV/09-errores-problem-details.txt"

# ---------------------------------------------------------------- E10
NEAR=$(new_mechanic "Mecanica Cercana" -34.6040 -58.3820 "FALLAS_${RUN_ID}")
{
  header "E10 · Fallas remotas, timeouts y reintento"
  echo "## dispatch-service sin responder (contenedor pausado): el alta se confirma y la asistencia queda REQUESTED"
  echo "\$ docker compose pause dispatch-service"
  "${DC[@]}" pause dispatch-service 2>&1
  CID="ev-falla-dispatch-${RUN_ID}"
  new_client_vehicle fallas
  req POST "$A/api/assistances" "$(assistance_body "FALLAS_${RUN_ID}")"
  F1=$(field id)
  echo "## reintento manual: vence el timeout de 5 s (DISPATCH_SERVICE_TIMEOUT) → 503"
  req POST "$A/api/assistances/$F1/dispatch"
  echo "\$ docker compose unpause dispatch-service"
  "${DC[@]}" unpause dispatch-service 2>&1
  wait_healthy $D
  echo
  echo "## reintento manual con dispatch-service disponible → MATCHED"
  req POST "$A/api/assistances/$F1/dispatch"
  req GET "$A/api/assistances/$F1/history"
  echo "## mechanic-service sin responder (pausado): vence el timeout de 3 s, dispatch-service responde 503 y assistance-service lo informa como 502"
  echo "\$ docker compose pause mechanic-service"
  "${DC[@]}" pause mechanic-service 2>&1
  CID="ev-falla-mechanic-${RUN_ID}"
  req POST "$A/api/assistances" "$(assistance_body "FALLAS_${RUN_ID}")"
  F2=$(field id)
  req POST "$A/api/assistances/$F2/dispatch"
  echo "\$ docker compose unpause mechanic-service"
  "${DC[@]}" unpause mechanic-service 2>&1
  wait_healthy $M
  echo
  echo "## reintento con mechanic-service disponible → MATCHED"
  req POST "$A/api/assistances/$F2/dispatch"
  echo "## logs de las fallas"
  echo "\$ docker compose logs assistance-service dispatch-service | grep -E 'ev-falla-' | grep -E 'WARN|ERROR' | grep 'c.u.m.'"
  "${DC[@]}" logs --no-color assistance-service dispatch-service | grep -E "ev-falla-" | grep -E "WARN|ERROR" \
    | grep "c.u.m." | sort -t'|' -k2 || true
} >"$EV/10-fallas-remotas-y-reintento.txt"

# ---------------------------------------------------------------- E11
shot "http://localhost:8080" "$EV/capturas/11-consola-web.png" 1440 1400
shot "https://github.com/fedeb123/mecanicosYa_backend/actions" "$EV/capturas/01-github-actions.png" 1440 1100

echo "== Evidencias generadas en $EV (el entorno sigue levantado; para detenerlo: docker compose down)"
