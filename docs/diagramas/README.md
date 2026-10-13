# Diagramas

Cada diagrama está en dos formatos:

- `NN-nombre.html`: página autocontenida (SVG inline), se abre en cualquier navegador.
- `png/NN-nombre.png`: imagen para insertar en el informe o en la presentación.

| # | Diagrama | Qué muestra | Requisito de la consigna |
|---|---|---|---|
| 01 | [Componentes](01-componentes.html) | Microservicios, bases por servicio, librería `shared-observability` y clientes REST | TP Inicial · diagrama de componentes |
| 02 | [Despliegue](02-despliegue.html) | Contenedores de Docker Compose, imágenes, puertos y volumen | TP Inicial · diagrama de despliegue |
| 03 | [Arquitectura en capas](03-capas.html) | Las cuatro capas comunes y la capa transversal | Primera Parte · arquitectura en capas |
| 04 | [Capas · mechanic-service](04-capas-mechanic.html) | Clases de mechanic-service por capa | Primera Parte · capa de servicios |
| 05 | [Capas · assistance-service](05-capas-assistance.html) | Clases de assistance-service por capa | Primera Parte · capa de servicios |
| 06 | [Capas · dispatch-service](06-capas-dispatch.html) | Clases de dispatch-service por capa | Primera Parte · capa de servicios |
| 07 | [Clases · mechanic-service](07-clases-mechanic.html) | Fachada, factory, repositorio, entidad y enum | TP Inicial · diagrama de clases |
| 08 | [Clases · assistance-service · dominio](08-clases-assistance-dominio.html) | Cliente, vehículo, solicitud, historial y enums con cardinalidades | TP Inicial · diagrama de clases |
| 09 | [Clases · assistance-service · aplicación](09-clases-assistance-aplicacion.html) | Fachada, eventos, observers, puerto y adaptador REST | TP Inicial · diagrama de clases |
| 10 | [Clases · dispatch-service](10-clases-dispatch.html) | Fachada, puerto, estrategias, entidad y repositorio | TP Inicial · diagrama de clases |
| 11 | [Patrones](11-patrones.html) | Factory, Repository, Strategy, Observer y Facade en el flujo | Primera Parte · patrones de diseño |
| 12 | [Secuencia · pedido de asistencia](12-secuencia-pedido.html) | Flujo principal entre los tres servicios | Primera Parte · modelado de procesos |
| 13 | [Secuencia · falla y reintento](13-secuencia-reintento.html) | Alta con dispatch-service sin responder y reintento manual | Primera Parte · modelado de procesos |
| 14 | [Ciclo de vida de la asistencia](14-estados-asistencia.html) | Máquina de estados de `AssistanceRequest` | Primera Parte · modelado de procesos |
| 15 | [Flujo de decisión del despacho](15-flujo-despacho.html) | Decisiones de `DispatchFacade` y la estrategia | Primera Parte · modelado de procesos |
| 16 | [Acceso local y remoto](16-local-remoto.html) | Invocación directa, eventos e interfaz con implementación REST | TP Inicial · acceso local y remoto |

Los diagramas siguen el sistema de diseño de la skill *diagram-design* (conectores ortogonales, un único color de foco por diagrama y leyenda al pie).
