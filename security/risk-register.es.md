# Registro de Riesgos de Seguridad de SecurePay

Versión en inglés: [Read in English](risk-register.md)

## Propósito

Este registro convierte el modelo de amenazas del flujo de pagos en trabajo de seguridad con responsable, prueba y revisión antes de cada liberación. Las calificaciones son cualitativas y deben revisarse cuando cambien la arquitectura, el proveedor o la clasificación de datos.

## Método de calificación

| Nivel | Significado |
|---|---|
| Crítico | Puede causar fraude, exposición amplia de datos sensibles o pérdida de control de producción. Bloquea la liberación hasta mitigarse. |
| Alto | Puede afectar materialmente la integridad de pagos, los datos de clientes o la disponibilidad. Requiere mitigación documentada y aprobación de liberación. |
| Medio | Impacto relevante pero acotado. Debe mitigarse en el ciclo planificado y seguirse hasta su cierre. |

## Riesgos activos

| ID | Riesgo | Nivel | Controles requeridos | Evidencia de validación | Responsable |
|---|---|---|---|---|---|
| PAY-01 | Un llamador no autenticado o no autorizado crea un pago. | Crítico | Autenticar cada solicitud; validar propiedad de cliente y comercio; usar roles de mínimo privilegio. | Pruebas automáticas de autorización; registro de revisión de acceso. | Equipo API |
| PAY-02 | Un reintento o repetición causa un cobro duplicado. | Crítico | Exigir clave de idempotencia; asociarla al llamador autenticado y huella de la solicitud; imponer almacenamiento único y repetir la respuesta de forma segura. | Prueba concurrente de solicitudes duplicadas; revisión de restricción de base de datos. | Equipo de pagos |
| PAY-03 | Se exponen tarjetas u otros datos sensibles. | Crítico | Aceptar sólo tokens del proveedor; nunca guardar PAN ni CVV; cifrar almacenes aprobados; enmascarar logs y errores. | Escaneo de logs; revisión de esquema; prueba de integración de tokenización. | Equipo de pagos |
| PAY-04 | Se altera el estado o importe de un pago. | Alto | Validar importe, moneda, comercio y propiedad en el servidor; restringir transiciones; verificar firmas de webhooks. | Pruebas API negativas; pruebas de transición; prueba de firma de webhook. | Equipo de pagos |
| PAY-05 | Una respuesta tardía o ausente del proveedor produce un estado final incorrecto. | Alto | Usar `UNKNOWN` para resultados sin resolver; reconciliar con el proveedor; mantener la reconciliación idempotente y auditable. | Prueba de timeout simulado; ejercicio del runbook de reconciliación. | Equipo de pagos |
| PAY-06 | Las acciones sensibles no pueden investigarse o se repudian. | Medio | Emitir eventos de auditoría inmutables con actor, ID de pago, resultado, fecha y trace ID; restringir acceso a logs. | Prueba de eventos de auditoría; revisión de retención y acceso. | Equipo de plataforma |
| PAY-07 | Tráfico abusivo agota la API. | Alto | Aplicar límites de tasa y cuotas por llamador; fijar límites de payload y timeouts; monitorear saturación y fallos. | Prueba de carga/límites; verificación de alertas. | Equipo de plataforma |
| PAY-08 | Secretos o credenciales del proveedor se exponen o tienen privilegios excesivos. | Crítico | Guardar secretos en un gestor aprobado; rotar credenciales; prohibir secretos en código, imágenes y logs; otorgar mínimos privilegios. | Escaneo de secretos; revisión de políticas; ejercicio de rotación. | Equipo de plataforma |

## Criterios de liberación

Un release del servicio de pagos no puede avanzar si:

- Existe un riesgo Crítico abierto o aceptado sin aprobación explícita del liderazgo de seguridad.
- Pasan las pruebas de autorización, idempotencia, transiciones de estado y verificación de webhooks.
- Se revisaron logs, errores de API y esquema de base de datos para detectar datos de pago prohibidos.
- La reconciliación de pagos `UNKNOWN` tiene responsable, dashboard y runbook probado.
- Los secretos se proveen solamente mediante gestión de secretos aprobada en tiempo de ejecución.

## Excepciones

Cada aceptación de riesgo debe indicar el ID del riesgo, justificación de negocio, controles compensatorios, autoridad que aprueba, fecha de vencimiento e issue de remediación. Las excepciones vencidas bloquean el release afectado.
