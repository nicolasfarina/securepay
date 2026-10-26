# Día 41 — Indicadores y objetivos de nivel de servicio

## Propósito

Definir cómo reconoceríamos que la API de pagos brinda un servicio confiable y qué datos hacen falta para medirlo. Los objetivos que siguen son **propuestas iniciales para el proyecto de aprendizaje**, no compromisos de producción aprobados por negocio u operaciones.

## SLI y SLO propuestos

| SLI | Definición | SLO inicial propuesto | Cohorte y ventana |
| --- | --- | --- | --- |
| Disponibilidad de la API | Solicitudes elegibles completadas sin error de servidor ni fallo de transporte, dividido por todas las solicitudes elegibles. | Al menos **99,5%** en una ventana móvil de 30 días. | Rutas `/api/payments`; separar por método y plantilla de ruta. Los rechazos 4xx, incluidos 401/403/404/409/422/429, cuentan como respuesta disponible, aunque no como operación de negocio exitosa. |
| Latencia de pagos | Solicitudes elegibles de pagos que completan en **500 ms o menos**, dividido por las solicitudes elegibles completadas. | Al menos **95%** en una ventana móvil de 30 días. | Medir por método y plantilla de ruta. Excluir errores del cliente 4xx del numerador y denominador de latencia; informarlos aparte. Las solicitudes 5xx o sin respuesta cuentan como malas en disponibilidad. |

La disponibilidad se mide por solicitudes y no por minutos de uptime. Por eso su presupuesto de error es el **0,5% de las solicitudes elegibles** en la ventana, no una cantidad de minutos. El presupuesto de latencia es el 5% de las solicitudes elegibles que puede superar 500 ms.

## Instrumentación existente

- Spring Boot instrumenta las solicitudes MVC con `http.server.requests`, que incluye etiquetas de método, resultado HTTP y plantilla de URI cuando está disponible. Esto puede alimentar las cohortes propuestas sin usar IDs de pago, cliente o comercio como etiquetas.
- El contador `securepay.payment.status.transitions` mide transiciones confirmadas por la base. Es útil para tendencias de dominio, pero no mide disponibilidad HTTP ni latencia.
- Las trazas de Día 38/39 se muestrean al 10% por defecto. Una traza muestreada sirve para investigar una solicitud, pero no es un denominador confiable para un SLI.

## Brechas antes de tratar los SLO como operativos

El [laboratorio del Día 42](day42-actionable-alerting.md) prueba un backend efímero y un bucket de 500 ms. Las brechas descritas aquí siguen abiertas para un despliegue operativo y una ventana de 30 días.

1. **No hay backend de métricas configurado.** El endpoint `/actuator/metrics` sirve para diagnóstico puntual, está protegido por rol ADMIN y consulta el registro en memoria del proceso. No conserva una ventana móvil de 30 días y Spring Boot indica que este endpoint no debe usarse para scraping de producción.
2. **Falta distribución de latencias.** El temporizador HTTP aún no tiene histograma ni límites SLO configurados. Sin buckets exportados no podemos calcular de forma agregable el porcentaje de solicitudes bajo 500 ms.
3. **Faltan fallos fuera del proceso.** Si el proceso está caído o la conexión no llega a Spring, `http.server.requests` no registra la solicitud. Haría falta una sonda sintética externa o telemetría del balanceador para contar esos fallos.
4. **Los objetivos necesitan datos de una carga representativa.** No hay una línea base de producción, patrón de tráfico acordado ni requisito comercial que justifique por sí solo 99,5% o 500 ms. Revisar los valores con mediciones antes de adoptarlos.

## Decisiones de conteo

- Un 4xx demuestra que la API respondió y tomó una decisión; no significa que el usuario completó el pago. Para que los 429 no oculten un problema de capacidad, revisar los rechazos por rate limit como indicador separado.
- Un 5xx o fallo de transporte cuenta como malo para disponibilidad. Los errores esperados de validación, autenticación, autorización, propiedad e idempotencia no se consideran indisponibilidad.
- Para latencia, contar respuestas exitosas de las rutas de pago; no premiar latencia baja en respuestas fallidas. Los status y rutas deben seguir disponibles en las series exportadas.
- El SLI de pagos mide la API, no la autorización o liquidación de un proveedor externo: SecurePay todavía no integra una red de pagos real.

## Próximo paso

El Día 42 puede configurar histogramas y un backend de métricas local para probar consultas y alertas. Antes de publicar alertas o llamar a estos objetivos SLOs de producción, confirmar las metas, la política para 429, la ventana, los endpoints incluidos y la retención de datos con quienes operen el servicio.

Referencias: [métricas de Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html) y [API del endpoint Actuator Metrics](https://docs.spring.io/spring-boot/api/rest/actuator/metrics.html).
