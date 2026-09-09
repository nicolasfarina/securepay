# Modelo de amenazas del flujo de pagos de SecurePay

English version: [Read in English](securepay-payment-flow.md)

## Alcance

Este modelo cubre el flujo desde la solicitud de pago del cliente hasta el proveedor de pagos y el estado final del pago.

## Activos

- Datos del pago y detalles de la transacción
- Datos de identidad y autorización del cliente
- Claves de idempotencia
- Estado del pago y respuestas del proveedor
- Registros de auditoría

## Límites de confianza

1. Aplicación cliente hacia la API de SecurePay
2. Servicios de SecurePay hacia la base de datos
3. Servicio de pagos de SecurePay hacia el proveedor externo
4. Operadores de SecurePay hacia la infraestructura de producción

## Amenazas principales

| Amenaza | Impacto | Control inicial |
|---|---|---|
| Solicitud de pago no autorizada | Cobros fraudulentos | Autenticación, autorización y validación de la transacción |
| Solicitud de pago duplicada | Cobro doble al cliente | Guardar y verificar la clave de idempotencia antes de procesar |
| Exposición de datos de pago | Daño financiero y de privacidad | TLS, cifrado en reposo, tokenización y gestión de secretos |
| Alteración del estado del pago | Decisiones de negocio incorrectas | Transiciones de estado del lado del servidor y respuestas firmadas |
| Repetición de una solicitud antigua | Operación repetida o fraudulenta | Idempotencia, expiración de solicitudes y autenticación |
| Falta de respuesta del proveedor | Estado de pago inconsistente | Estado `UNKNOWN` y proceso de reconciliación |
| Datos sensibles en logs | Filtración de información | Logs estructurados con enmascaramiento y controles de acceso |

## Requisitos de seguridad

- Nunca almacenar números de tarjeta ni CVV sin tokenizar en SecurePay.
- Exigir autenticación y autorización para cada operación de pago.
- Validar importe, moneda, comercio y propietario del lado del servidor.
- Exigir una `Idempotency-Key` única para cada operación de pago.
- Permitir cambios de estado solo mediante transiciones controladas.
- Registrar eventos relevantes de seguridad sin datos sensibles del pago.
- Reconciliar los pagos `UNKNOWN` con el proveedor antes de resolverlos definitivamente.

## Riesgo residual

Un proveedor externo puede aprobar un pago mientras SecurePay no puede recibir temporalmente la respuesta. SecurePay debe mantener el pago como `UNKNOWN` y reconciliarlo, en lugar de reintentarlo ciegamente o marcarlo como `REJECTED`.
