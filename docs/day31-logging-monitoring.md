# Día 31 — Registro y monitoreo de seguridad

Fecha: 2026-10-23 (America/Buenos_Aires).

Revisión estática de señales existentes. No se añadieron logs, métricas, alertas ni infraestructura de monitoreo.

## Qué aprendemos

Un log sirve para reconstruir qué ocurrió; una métrica muestra tendencias; una alerta pide actuar cuando una señal cruza un umbral. Registrar sin proteger datos sensibles evita una filtración, pero registrar demasiado poco impide detectar abuso e investigar incidentes.

## Evidencia observada

- No se encontró un logger de aplicación ni una auditoría persistente para login, denegaciones de acceso, creación de pagos o cambios de estado.
- `JwtAuthenticationFilter` limpia el contexto cuando el token es inválido, pero no emite un evento de seguridad. `AuthController` responde `401` con mensaje genérico ante credenciales incorrectas.
- Actuator expone solo `health`; los detalles están ocultos y health es anónimo. No hay endpoint de métricas expuesto ni métricas propias configuradas en el manifiesto revisado.
- El runbook de pagos desconocidos describe la evidencia de auditoría que debería registrarse cuando exista reconciliación, pero el flujo actual aún no produce esos eventos.
- Los logs del framework pueden emitir diagnósticos de validación; antes de usarlos en producción hay que comprobar que no incluyan secretos o contenido sensible del request.

## Evaluación y límites

**Actualización 2026-10-26:** los [Días 36–39](day36-structured-logging.md) agregaron logs estructurados, Correlation ID, métricas y trazas. No son una auditoría completa de eventos de seguridad ni cierran los requisitos de retención, acceso y alertas; los párrafos de evidencia anteriores describen la revisión del 23/10.

La capacidad actual no permite atribuir intentos fallidos a una identidad con seguridad, detectar patrones de abuso ni demostrar quién ejecutó una acción administrativa. PAY-06 sigue siendo un riesgo abierto. El health check sirve para disponibilidad básica, no para detección de fraude o seguridad.

## Práctica pendiente

Definir eventos mínimos para `login_failed`, `authorization_denied`, `payment_created` y `payment_reconciled`: actor, resultado, hora, identificador de correlación y objeto afectado; excluir contraseñas, JWT completos, secretos y datos de tarjeta. Después decidir retención, acceso y alertas.

## Fuentes

- [OWASP A09:2021 — Security Logging and Monitoring Failures](https://owasp.org/Top10/en/A09_2021-Security_Logging_and_Monitoring_Failures/)
- [Riesgo PAY-06](../security/risk-register.md)
- [Runbook de reconciliación `UNKNOWN`](../security/runbooks/unknown-payment-reconciliation.md)
