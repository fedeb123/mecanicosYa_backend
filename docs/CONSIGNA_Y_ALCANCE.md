# Adaptación del Trabajo Práctico Primera Parte

## Separación respecto del proyecto móvil

Este trabajo conserva el problema de negocio de MecanicosYa, pero constituye una entrega backend independiente. La aplicación Android, Retrofit, pantallas y navegación se desarrollan en otra materia y no se incluyen aquí.

Los endpoints quedan listos para que un cliente móvil pueda integrarse posteriormente.

## Matriz de cumplimiento

| Actividad obligatoria | Implementación |
|---|---|
| Framework empresarial | Tres aplicaciones Java 25 con Spring Boot 4.1.1 |
| Patrones Factory, Repository, Strategy, Observer y Facade | Aplicados explícitamente en dominio y aplicación |
| Servicios orientados a componentes | Mecánicos, Asistencias y Despacho |
| Interfaces internas | Puertos `MechanicDirectory` y `DispatchPort` |
| Eventos de dominio | `AssistanceRequestedEvent` y listener posterior al commit |
| Modelado de procesos | Diagramas de secuencia, máquina de estados y flujo de decisión del despacho |
| Arquitectura en capas | API, aplicación, dominio e infraestructura |
| Aplicación funcional | Flujo registrar mecánico -> pedir asistencia -> asignar mecánico |
| Documentación de patrones | Tabla y referencias en `ARQUITECTURA.md` |
| Gestión de dependencias | Maven multi-módulo |

## Casos de uso

1. Registrar un mecánico y sus habilidades.
2. Consultar un mecánico.
3. Actualizar disponibilidad y ubicación.
4. Buscar mecánicos disponibles por habilidad.
5. Crear una solicitud de asistencia.
6. Publicar internamente el evento de dominio de solicitud creada.
7. Invocar remotamente al servicio de despacho.
8. Consultar remotamente candidatos en `mechanic-service`.
9. Elegir el mejor candidato con una estrategia intercambiable.
10. Persistir y consultar el resultado de despacho.
11. Actualizar la asistencia con el mecánico asignado.

## Fuera de alcance de esta parte

- Aplicación Android (el repositorio incluye sólo una consola web de pruebas).
- SOAP y WSDL.
- RabbitMQ, Kafka u otra cola.
- API externa real.
- Inteligencia artificial.
- Pagos y geolocalización en tiempo real.
- Autenticación de producción.

Esos puntos aparecen en la Segunda Parte o el Integrador de la consigna y podrán sumarse después sin impedir la entrega inicial.

