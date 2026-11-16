#!/usr/bin/env bash
# Baja el contrato OpenAPI de cada servicio a docs/contratos/. Necesita el compose levantado.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p docs/contratos

for entry in mechanic-service:8081 assistance-service:8082 dispatch-service:8083 identity-service:8084; do
  service="${entry%%:*}"
  port="${entry##*:}"
  curl -fsS "http://localhost:${port}/v3/api-docs.yaml" -o "docs/contratos/${service}.yaml"
  echo "docs/contratos/${service}.yaml"
done
