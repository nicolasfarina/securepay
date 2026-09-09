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

## Clasificación STRIDE

| Categoría | Ejemplo en SecurePay | Control |
|---|---|---|
| Spoofing (suplantación) | Un atacante usa la identidad de otro cliente. | Autenticación y MFA para operaciones sensibles |
| Tampering (alteración) | Un atacante cambia el importe del pago. | Validación del lado del servidor y controles de integridad |
| Repudiation (repudio) | Un usuario niega haber realizado un pago. | Logs de auditoría inmutables y trace IDs |
| Information Disclosure (divulgación) | Los datos de pago aparecen en un log o respuesta. | TLS, tokenización, enmascaramiento y controles de acceso |
| Denial of Service (denegación de servicio) | La API de pagos recibe demasiadas solicitudes. | Rate limiting, cuotas y monitoreo |
| Elevation of Privilege (elevación de privilegios) | Un cliente accede a una operación administrativa. | Autorización basada en roles y mínimo privilegio |

## Riesgo residual

Un proveedor externo puede aprobar un pago mientras SecurePay no puede recibir temporalmente la respuesta. SecurePay debe mantener el pago como `UNKNOWN` y reconciliarlo, en lugar de reintentarlo ciegamente o marcarlo como `REJECTED`.
