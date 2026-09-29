# Runbook: Reconciliación de Pagos `UNKNOWN`

Versión en inglés: [Read in English](unknown-payment-reconciliation.md)

## Cuándo usar este runbook

Usar este runbook cuando SecurePay no puede determinar el resultado final de un pago, por ejemplo tras un timeout del proveedor, interrupción de red o webhook sin confirmar. No marcar el pago como `APPROVED` ni `REJECTED` basándose sólo en una suposición.

## Precondiciones

- El operador tiene el rol aprobado de operaciones de pagos.
- Están disponibles el registro de pago, eventos de auditoría, trace ID, referencia del proveedor y clave de idempotencia.
- El acceso a la consola del proveedor sigue mínimo privilegio y queda auditado.

## Procedimiento

1. Localizar el pago por su ID de SecurePay. Confirmar que el estado sea `UNKNOWN`; no crear un pago de reemplazo.
2. Reunir el contexto inmutable: identificadores de cliente y comercio, importe, moneda, hora de creación, referencia del proveedor, hash de la clave de idempotencia y trace ID. No copiar tokens ni datos de pago sensibles a tickets o chats.
3. Consultar al proveedor con la referencia del proveedor. Si no está disponible, usar el mecanismo aprobado de búsqueda por idempotencia o comercio. Registrar la hora y resultado de la consulta en la auditoría.
4. Aplicar el resultado exactamente una vez:
   - El proveedor confirma un cargo exitoso: transicionar a `APPROVED`.
   - El proveedor confirma un fallo definitivo sin cargo: transicionar a `REJECTED`.
   - El resultado sigue ausente, ambiguo o inconsistente: mantener `UNKNOWN` y escalar.
5. Verificar que el estado resultante, referencia del proveedor, actor de reconciliación, origen, fecha y trace ID aparezcan en el log de auditoría.
6. Notificar al cliente sólo mediante el flujo aprobado de comunicaciones. No prometer un resultado final mientras el pago permanezca `UNKNOWN`.

## Escalamiento

Escalar al ingeniero on-call de pagos y al canal de soporte del proveedor cuando:

- El proveedor no confirma el resultado dentro de la ventana de servicio acordada.
- Los registros de SecurePay y del proveedor no coinciden.
- Más de un intento de reconciliación informa resultados diferentes.
- La reconciliación revela un cargo duplicado o potencialmente fraudulento.

Escalar una sospecha de exposición de datos, acceso no autorizado o problema de credenciales mediante el proceso de incidentes de seguridad de inmediato.

## Salvaguardas

- La reconciliación debe ser idempotente: repetirla no puede crear un cargo adicional ni un evento de auditoría duplicado.
- Sólo el flujo de reconciliación puede resolver `UNKNOWN`; se prohíben ediciones manuales en base de datos.
- Nunca reintentar un nuevo cargo al proveedor sólo porque el resultado anterior sea desconocido.
- Conservar respuestas originales del proveedor y referencias de auditoría según la política de retención.

## Criterio de finalización

La ejecución termina sólo cuando el pago tiene un estado final verificado por el proveedor o permanece explícitamente `UNKNOWN` con un registro de escalamiento activo. Vincular el evento de auditoría y caso/referencia del proveedor al registro operacional.
