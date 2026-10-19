# Despliegue en OpenShift (Developer Sandbox)

Levanta el mismo sistema que `docker compose` en un proyecto de OpenShift, con una URL pública https para mostrar la consola web. Pensado para el [Developer Sandbox de Red Hat](https://developers.redhat.com/developer-sandbox) (gratis, 30 días, un proyecto por usuario).

## Qué se despliega

| Recurso | Imagen | Notas |
|---|---|---|
| `postgres` | `postgres:16-alpine` | Volumen de 1 GiB. Crea las 3 bases y usuarios la primera vez. Sin PostGIS: el código no lo usa. |
| `mechanic-service`, `dispatch-service`, `assistance-service` | construidas en el cluster con `Dockerfile.service` | Mismas variables que el compose. Probes en `/actuator/health/liveness` y `/readiness`. |
| `frontend` | construida con `Dockerfile.frontend` (nginx sin root, puerto 8080) | Proxy a los tres servicios, como en el compose. |
| Route `mecanicosya` | | Entrada pública de la consola, https. |
| Routes `mechanic-service`, `dispatch-service`, `assistance-service` | | Opcionales (`swagger-routes.yaml`), para mostrar el Swagger de cada servicio en `/swagger-ui.html`. La base no se expone. |

Diferencias con el compose, todas por cómo corre OpenShift:

- Los contenedores corren con un UID aleatorio, sin root. Por eso nginx escucha en 8080 (`nginx.conf` de esta carpeta) y Postgres usa una subcarpeta del volumen.
- Las imágenes base salen de `mirror.gcr.io` para no chocar con el límite de descargas de Docker Hub en un cluster compartido.
- Los healthchecks con `curl` pasan a ser probes HTTP, así que la imagen no instala curl.

Nada de esto toca el compose ni `infra/`.

## Cómo desplegar

1. Entrá al sandbox, abrí la consola web y, en el menú del usuario, elegí **Copy login command**. Pegá ese `oc login --token=... --server=...` en una terminal. Hace falta el cliente `oc` (en Mac: `brew install openshift-cli`).
2. Desde la raíz del repo:

   ```bash
   ./openshift/desplegar.sh
   ```

   Construye las cuatro imágenes dentro del cluster con el código local (`oc start-build --from-dir`), aplica los manifiestos, espera a que todo esté listo e imprime la URL. La primera vez tarda unos minutos porque Maven descarga dependencias en cada build.
3. Abrí la URL. La pestaña **Demo rápida** de la consola recorre el flujo completo hasta `MATCHED`.

Volver a correr el script reconstruye las imágenes y redespliega. Los datos quedan en el volumen de Postgres.

## Antes de mostrarlo

El sandbox apaga los pods que llevan 12 horas corriendo. Un rato antes de la demo:

```bash
oc scale deployment -l app.kubernetes.io/part-of=mecanicosya --replicas=1
oc get pods
```

Los servicios Java tardan alrededor de un minuto en quedar listos.

## Problemas frecuentes

- **Un build queda en `Pending` o un pod no arranca por cuota**: `oc describe quota` muestra cuánto queda. El script corre los builds de a uno; si hay pods corriendo y la cuota no alcanza para un build, bajalos con `oc scale deployment -l app.kubernetes.io/part-of=mecanicosya --replicas=0` y volvé a correr el script.
- **Ver logs**: `oc logs deployment/assistance-service` (o el servicio que sea). El `X-Correlation-Id` que muestra la consola sirve para seguir un pedido entre servicios.
- **Empezar de cero**: `oc delete all,pvc,secret,configmap,route,is,bc -l app.kubernetes.io/part-of=mecanicosya` y de nuevo `./openshift/desplegar.sh`.
