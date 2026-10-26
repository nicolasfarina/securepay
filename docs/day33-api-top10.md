# Día 33 — Mapeo de SecurePay contra OWASP API Security Top 10

Fecha: 2026-10-23 (America/Buenos_Aires).

Este mapeo usa la edición OWASP API Security Top 10 2023. Es una revisión del código y de pruebas existentes; no es una certificación ni una prueba de penetración.

**Actualización 2026-10-26:** la tabla conserva la fotografía del 23/10. El [Día 34](day34-rate-limiting.md) agregó límites en memoria para login y creación de pagos; siguen abiertos los límites de negocio, la configuración de proxies confiables y la coordinación entre réplicas.

## Mapeo de riesgos

| Riesgo OWASP API 2023 | Evidencia actual | Estado de revisión |
|---|---|---|
| API1 Broken Object Level Authorization | `GET /api/payments/{id}` verifica propiedad del cliente; pruebas de Días 23–24 comprueban acceso ajeno y respuesta no reveladora. El listado administrativo requiere rol ADMIN. | Control observado y cubierto por pruebas existentes. |
| API2 Broken Authentication | Login valida credenciales; JWT verifica firma, emisor, rol y expiración; hay pruebas de tokens inválidos. Cuentas en memoria y credenciales demo no son identidad productiva; falta limitación de intentos. | Control parcial; preparación productiva abierta. |
| API3 Broken Object Property Level Authorization | El cliente solo puede crear campos declarados en `CreatePaymentRequest`; respuesta usa `PaymentResponse` explícito. Propiedades de pago expuestas son visibles para su dueño y para ADMIN. | Modelo acotado; revisar cada campo nuevo y no aceptar campos de estado/propiedad desde cliente. |
| API4 Unrestricted Resource Consumption | No hay rate limit ni cuota. El listado admin trae todos los pagos sin paginación. Los límites de importe/campos no sustituyen límites de solicitudes, bytes o concurrencia. | Exposición pendiente; relacionado con Día 34 / PAY-07. |
| API5 Broken Function Level Authorization | Crear/listar/ver usan roles CUSTOMER/ADMIN y Spring Method Security. Pruebas existentes cubren matriz de roles. | Controles observados; conservar pruebas al añadir rutas. |
| API6 Unrestricted Access to Sensitive Business Flows | Cada clave idempotente nueva crea otra solicitud; no hay límite de transacciones o exposición por cliente. | Riesgo documentado en Día 26; regla comercial pendiente. |
| API7 Server Side Request Forgery | No se encontró llamada saliente en código productivo ni URL controlada por el cliente. | Sin ruta actual identificada; revisar al integrar proveedor. |
| API8 Security Misconfiguration | `prod` deshabilita OpenAPI/Swagger y oculta detalles; el perfil predeterminado es `local`; en perfil `postgres`, la documentación permanece habilitada. | Riesgo condicional al perfil/despliegue, revisado en Día 27. |
| API9 Improper Inventory Management | Se identificaron rutas de login, salud, pagos y administración; OpenAPI está disponible en perfiles no productivos según configuración. No hay registro de inventario de API desplegadas/versiones. | Inventario del código parcial; el inventario de entornos requiere evidencia operativa. |
| API10 Unsafe Consumption of APIs | La aplicación raíz no consume APIs externas. | Sin consumidor actual; integración del proveedor requerirá validar autenticidad, TLS, errores y datos recibidos. |

## Qué aprendemos

La lista API enfoca riesgos propios de interfaces de servicio. API1 y API5 se parecen, pero API1 protege un objeto concreto y API5 una función completa. API3 examina qué propiedades se pueden leer o cambiar. API4 limita presión técnica; API6 protege una operación de negocio sensible. El mismo endpoint puede aparecer en más de una categoría por motivos distintos.

## Límites y seguimiento

Los estados «control observado» describen código y pruebas existentes, no ausencia de vulnerabilidades. Los perfiles y rutas reales dependen del despliegue. API4, API6 y API8 se conectan directamente con los pendientes de rate limiting, límites transaccionales y selección segura de perfiles. El mapeo deberá revisarse cuando se integre un proveedor o se agreguen roles y operaciones.

## Fuentes

- [OWASP API Security Project](https://owasp.org/projects/api-security-project)
- [Documentación oficial de API Security Top 10 2023](https://api-security.owasp.org/editions/2023/en/0x00-header/)
- [Matriz de autorización y evidencia de Días 23–24](day23-access-control.md)
- [Revisión de diseño de Día 26](day26-insecure-design.md)
