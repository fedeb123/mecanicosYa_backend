#!/usr/bin/env bash
# Despliega MecanicosYa en el proyecto de OpenShift en el que estés logueado (oc login).
# Uso, desde la raíz del repo:  ./openshift/desplegar.sh
# Volver a correrlo reconstruye las imágenes con el código local y redespliega.
set -euo pipefail
cd "$(dirname "$0")/.."

oc whoami >/dev/null 2>&1 || { echo "Primero logueate: copiá el comando 'oc login' desde la consola del sandbox."; exit 1; }
echo "Proyecto: $(oc project -q)"
oc describe quota 2>/dev/null | grep -E '^(Name|Resource|limits|requests)' || true

oc apply -f openshift/builds.yaml
oc apply -f openshift/postgres.yaml

# Los builds corren de a uno para no pasar la cuota de CPU del sandbox.
for image in mechanic-service dispatch-service assistance-service frontend; do
  echo "== Construyendo $image"
  oc start-build "$image" --from-dir=. --exclude='(^|/)(\.git|target|node_modules)(/|$)' --follow --wait
done

oc apply -f openshift/servicios.yaml
oc apply -f openshift/frontend.yaml

for deploy in postgres mechanic-service dispatch-service assistance-service frontend; do
  oc rollout status "deployment/$deploy" --timeout=10m
done

echo
echo "Listo: https://$(oc get route mecanicosya -o jsonpath='{.spec.host}')"
