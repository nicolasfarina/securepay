# Plan de Verificación de Seguridad de SecurePay

Versión en inglés: [Read in English](security-verification-plan.md)

## Propósito

Este plan define la evidencia mínima para demostrar que los controles del [registro de riesgos de seguridad](risk-register.es.md) funcionan como se espera. Complementa las pruebas funcionales; no reemplaza la revisión manual de seguridad.

## Niveles de prueba

| Nivel | Cuándo se ejecuta | Objetivo |
|---|---|---|
| Unitaria | En cada cambio | Verificar validación, decisiones de autorización, transiciones de estado y enmascaramiento de datos de forma aislada. |
| Integración | Pull request y candidato de release | Verificar restricciones de base de datos, límites con el proveedor, tokenización y validación de webhooks. |
| Extremo a extremo | Candidato de release | Verificar el ciclo de vida completo del pago, incluyendo fallos y reconciliación. |
| Operacional | Antes de producción y periódicamente | Verificar accesos, rotación de secretos, alertas, consulta de auditoría y runbooks de incidentes. |

## Verificación requerida por riesgo

| Riesgo | Controles automatizados | Controles manuales / operacionales | Condición para aprobar |
|---|---|---|---|
| PAY-01 Pago no autorizado | Solicitudes sin credenciales, con credenciales inválidas y sobre el pago de otro cliente no devuelven datos ni crean pagos. | Revisar roles y accesos de cuentas privilegiadas. | Sólo actores autorizados realizan cada operación. |
| PAY-02 Pago duplicado o repetido | Enviar solicitudes idénticas concurrentes; reintentar tras timeout del cliente; reutilizar una clave con un cuerpo cambiado. | Revisar vencimiento y retención de claves de idempotencia. | Como máximo un cobro al proveedor; reintentos equivalentes devuelven el resultado original; reutilización distinta se rechaza. |
| PAY-03 Exposición de datos sensibles | Comprobar que logs, errores, trazas y persistencia omiten PAN y CVV; escanear artefactos por secretos. | Inspeccionar configuración de tokenización del proveedor. | Datos de pago prohibidos y credenciales no aparecen fuera de sistemas aprobados de secretos/tokens. |
| PAY-04 Alteración | Alterar importe, moneda, propiedad, estado y firma de webhook. | Revisar la política de transiciones permitidas. | Entradas inválidas y webhooks sin firma o con firma inválida se rechazan; transiciones ilegales no persisten. |
| PAY-05 Resultado desconocido | Simular pérdida de conexión y timeout antes y después del procesamiento del proveedor. | Ejecutar el runbook de reconciliación con un registro controlado. | El resultado permanece `UNKNOWN` hasta resolverse; la reconciliación es idempotente y auditable. |
| PAY-06 Repudio | Comprobar que acciones sensibles exitosas y fallidas emiten un evento con actor, ID de pago, hora, resultado y trace ID. | Recuperar un evento como investigador aprobado y validar retención. | El evento es completo, buscable y está protegido de mutaciones rutinarias. |
| PAY-07 Agotamiento de recursos | Probar límites de tasa, tamaño de payload, timeout y concurrencia. | Verificar dashboards y alertas mediante un umbral controlado. | El exceso de tráfico se limita sin exponer datos ni desestabilizar pagos. |
| PAY-08 Exposición de secretos | Ejecutar escaneo de secretos, dependencias e imágenes en CI. | Revisar acceso a secretos en runtime y realizar una rotación. | No hay secretos en el repositorio; sólo identidades aprobadas acceden a los secretos necesarios. |

## Criterios de calidad de CI

Todo pull request que cambie pagos, autenticación, autorización, persistencia, webhooks o infraestructura debe ejecutar:

- Pruebas unitarias y de integración del control afectado.
- Análisis estático y escaneo de vulnerabilidades de dependencias.
- Escaneo de secretos en código y configuración generada.
- Pruebas que impidan registrar o devolver datos de pago prohibidos.

Los candidatos de release deben ejecutar además el escenario end-to-end de reconciliación `UNKNOWN` y el escenario de concurrencia de idempotencia.

El procedimiento operacional está definido en el [runbook de reconciliación de pagos `UNKNOWN`](runbooks/unknown-payment-reconciliation.es.md).

## Evidencia y retención

Adjuntar los resultados a la evidencia de build o release. Guardar la evidencia de revisión operacional con fecha, revisor, alcance, resultado e issue de remediación en caso de fallos. Un control fallido de riesgo Crítico bloquea el release hasta corregirse o aceptarse formalmente mediante el proceso de excepciones del registro de riesgos.
