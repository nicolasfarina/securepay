# Día 38 — Fundamentos de OpenTelemetry

## Conceptos

- **API**: instrumentos que usa el código para crear spans y registrar atributos/estado.
- **SDK**: recibe la telemetría de la API, aplica procesamiento y la exporta.
- **Span**: una operación medida. Una traza relaciona spans mediante contexto padre/hijo.
- **Context propagation**: conecta trabajo a través de hilos y solicitudes entre servicios.

OpenTelemetry Java separa la API del SDK. La aplicación puede instrumentar usando la API y
obtener un no-op cuando todavía no hay SDK; cuando el SDK está configurado, los mismos spans
pueden procesarse y exportarse. Este día practica instrumentación manual dentro de SecurePay;
la configuración de SDK/exporter y el rastreo de extremo a extremo quedan para el Día 39.

## Span implementado

`PaymentService.create` crea `payment.create`, lo hace actual mientras ejecuta la operación,
y marca `OK` o `ERROR`. Al éxito registra `payment.status`; al fallo solo registra
`error.type` y estado, sin el mensaje de excepción. No registra IDs de pago/cliente/comercio,
clave de idempotencia ni datos del pago. La operación envuelve la llamada a la transacción
aislada, que completa commit antes de devolver el resultado.

La configuración obtiene el `OpenTelemetry` global cuando no existe un bean configurado.
En producción sin SDK/Java agent, la API es no-op y no hay exportación. La prueba instala un
SDK y un exporter en memoria sin enviar spans a la red.

## Pruebas

`PaymentTracingTest` verifica nombre, estado, atributo de éxito, tipo de error, IDs de span y
que los atributos del span no incluyan identificadores sensibles ni eventos con mensajes.

```powershell
mvn -B '-Dtest=PaymentTracingTest' test
mvn -B verify
```

## Fuentes oficiales

- [Introducción a OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/intro/)
- [Grabar telemetría con la API Java](https://opentelemetry.io/docs/languages/java/api/)
- [Trazas](https://opentelemetry.io/docs/concepts/signals/traces/)
- [Propagación de contexto](https://opentelemetry.io/docs/concepts/context-propagation/)

## Evidencia ejecutada

El 2026-10-26, `scripts/test-integration.ps1` terminó con `BUILD SUCCESS`: 111 pruebas,
0 fallos, 0 errores y 0 omitidas, incluyendo PostgreSQL 18.6 temporal y Tomcat en HTTP real.
SpotBugs reportó 0 incidencias y 0 errores. La base temporal se eliminó al terminar.
