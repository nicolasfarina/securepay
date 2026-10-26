# Día 37 — Métricas con Micrometer

## Qué medimos

Spring Boot ya trae Actuator y Micrometer. Se agregó un contador de transiciones exitosas
de estado: `securepay.payment.status.transitions`, etiquetado por `from` y `to` con los
valores del enum `PaymentStatus`. No contiene customer ID, payment ID, merchant ID,
Correlation ID, URL ni idempotency key. Las etiquetas tienen un conjunto de valores acotado.

El contador aumenta tras el commit de la transacción, cuando la transición quedó persistida.
Una transición rechazada o revertida no genera una muestra. Las métricas sirven para tendencias agregadas;
no sustituyen logs para investigar un pago individual ni auditoría financiera.

## Acceso

Actuator expone `health` y `metrics`. Health sigue siendo público y oculta detalles. Las
rutas `/actuator/metrics` requieren rol ADMIN; los usuarios sin JWT reciben 401 y los
usuarios CUSTOMER reciben 403. El exporter Prometheus no se agregó: primero hace falta
definir el scraper y el acceso de red de producción.

## Verificación

- `PaymentMetricsTest` verifica el conteo y las únicas etiquetas de la métrica.
- `CorrelationIdHttpTest` verifica el endpoint metrics, sus restricciones de acceso y una
  transición persistida consultada con filtros por estado.
- Correr `mvn -B verify`; correr además `scripts/test-integration.ps1` para confirmar la
  suite con PostgreSQL temporal y HTTP real.

### Evidencia ejecutada

El 2026-10-26, `scripts/test-integration.ps1` terminó con `BUILD SUCCESS`: 109 pruebas,
0 fallos, 0 errores y 0 omitidas, incluyendo PostgreSQL 18.6 temporal y Tomcat en HTTP real.
SpotBugs reportó 0 incidencias y 0 errores. La base temporal fue eliminada por el script.

## Límites

La métrica es local a la instancia. En varias réplicas el scraper debe agregar series de
cada instancia. No etiquetar con valores por request: crearía cardinalidad sin límite,
costaría memoria y podría revelar identificadores operativos.
