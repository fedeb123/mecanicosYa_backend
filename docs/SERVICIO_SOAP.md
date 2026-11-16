# Servicio SOAP: clasificación del pedido

La consigna de la Segunda Parte pide al menos un servicio SOAP con WSDL y contrato. MecanicosYa expone por SOAP la misma clasificación del texto del pedido que ya existe por REST en `POST /api/triage`, así que no hay lógica duplicada: los dos adaptadores llaman a `TriageService`. El resto del sistema sigue siendo REST y Kafka.

## Contrato

Es *contract-first*: primero se escribió el esquema y el WSDL se arma a partir de él.

| Qué | Dónde |
|---|---|
| Esquema XSD (el contrato) | [`assistance-service/src/main/resources/ws/triage.xsd`](../assistance-service/src/main/resources/ws/triage.xsd), copia en [`contratos/triage.xsd`](contratos/triage.xsd) |
| WSDL publicado | `GET http://localhost:8082/ws/triage.wsdl`, copia en [`contratos/triage.wsdl`](contratos/triage.wsdl) |
| Endpoint | `POST http://localhost:8082/ws` |
| Namespace | `http://mecanicosya.com/ws/triage` |
| Servicio y operación | `TriageService`, puerto `TriagePort`, operación `ClassifyProblem` (SOAP 1.1, document/literal) |

`ClassifyProblemRequest` trae `description` (de 1 a 1000 caracteres) y `vehicleType` (opcional: `BICYCLE`, `E_BIKE` o `MOTORCYCLE`). `ClassifyProblemResponse` devuelve `category`, `problemType`, `requiredSkill`, `urgency` (`LOW`, `MEDIUM` o `HIGH`), `confidence` y `source` (`AI` o `KEYWORDS`), los mismos campos que la respuesta REST.

## Ejemplo

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:tri="http://mecanicosya.com/ws/triage">
  <soapenv:Body>
    <tri:ClassifyProblemRequest>
      <tri:description>Se me cortó la cadena y estoy parado en Palermo</tri:description>
      <tri:vehicleType>BICYCLE</tri:vehicleType>
    </tri:ClassifyProblemRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

```bash
curl -s -X POST http://localhost:8082/ws \
  -H "Content-Type: text/xml; charset=utf-8" \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @pedido.xml
```

La respuesta y los casos de error están en [`evidencias/segunda-parte/15-soap.txt`](evidencias/segunda-parte/15-soap.txt). En SoapUI o Postman alcanza con importar el WSDL y agregar el header `Authorization`.

## Decisiones

- **Por qué la clasificación.** Es una operación sin estado, con entrada y salida chicas y bien tipadas, que es el caso típico de un servicio SOAP. Además, el componente de IA queda consumible por los dos estilos.
- **Validación contra el esquema.** Un `PayloadValidatingInterceptor` valida el pedido y la respuesta contra el XSD. Un sobre inválido (por ejemplo `vehicleType` `TRUCK`, o una descripción vacía) recibe un SOAP Fault `Client` con el detalle y no llega al endpoint.
- **Seguridad.** El WSDL se lee sin token, porque es el contrato. Los mensajes a `/ws` piden el mismo JWT que el resto de la API (`Authorization: Bearer`), con cualquier rol; sin token la respuesta es `401`.
- **Stack.** Spring Web Services con la autoconfiguración de Spring Boot (`spring.webservices.path: /ws`) y JAXB para pasar de XML a objetos.

## Código

| Clase | Capa | Qué hace |
|---|---|---|
| `SoapConfiguration` | `config` | Publica el WSDL a partir del XSD y registra la validación |
| `TriageSoapEndpoint` | `api.soap` | Recibe `ClassifyProblemRequest` y llama a `TriageService` |
| `ClassifyProblemRequest`, `ClassifyProblemResponse` | `api.soap` | Los mensajes, mapeados con JAXB |
| `TriageSoapEndpointTest` | test | El WSDL sin token, un sobre válido, uno que rompe el esquema y uno sin token, por HTTP contra Postgres real |
