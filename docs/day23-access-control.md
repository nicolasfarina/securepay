# Día 23 — Control de acceso

Fecha de la sesión: 2026-10-21 (America/Buenos_Aires). Lección y práctica guiada completadas en español.

Código revisado para este registro: `a9cc7072cb52b5ae0afe00848d23e0abb76c2fca`. Las ejecuciones del alumno fueron sobre su copia local; no se verificó su SHA.

## Objetivo

Distinguir autenticación, autorización por rol y autorización por propiedad. Este cierre registra aprendizaje y pruebas existentes; no significa que toda la matriz tenga cobertura automatizada ni que todos los riesgos estén resueltos.

## Matriz de permisos esperados

Suponiendo un pago existente de `customer-123` y tokens válidos salvo la fila anónima:

| Usuario | GET /api/payments/{paymentId} | GET /api/admin/payments |
|---|---|---|
| Sin token válido | 401 | 401 |
| customer-123, dueño y rol CUSTOMER | 200 | 403 |
| customer-789, otro cliente con rol CUSTOMER | 404 | 403 |
| admin-123, solo rol ADMIN | 403 | 200 |

La tabla describe la política del código; no afirma que se hayan ejecutado todos sus casos durante la sesión.

- [SecurityConfig](../src/main/java/com/securepay/security/SecurityConfig.java) exige autenticación para las rutas protegidas y configura 401/403.
- [PaymentController](../src/main/java/com/securepay/payment/PaymentController.java) exige `CUSTOMER`; pasa `authentication.getName()` al servicio.
- [PaymentService.getForCustomer](../src/main/java/com/securepay/payment/PaymentService.java) compara el dueño almacenado con el usuario autenticado. Un pago ajeno genera la misma excepción que uno inexistente y devuelve 404.
- [AdminPaymentController](../src/main/java/com/securepay/payment/AdminPaymentController.java) exige `ADMIN` para el listado.

El rol ADMIN no concede automáticamente CUSTOMER: no hay una jerarquía de roles configurada.

## Conceptos trabajados

- **Escalada horizontal:** un cliente intenta leer un pago de otro cliente cambiando el ID.
- **Escalada vertical:** un cliente intenta usar la función de administración.
- Un UUID difícil de adivinar no reemplaza la autorización; el identificador puede conocerse por otros medios.
- El dueño al crear un pago se obtiene de la identidad autenticada. [CreatePaymentRequest](../src/main/java/com/securepay/payment/CreatePaymentRequest.java) no define un campo `customerId`.
- Ocultar un botón no protege el endpoint: el servidor debe verificar los permisos en cada petición.
- Se practicó la distinción: 401 por autenticación inválida, 403 por rol insuficiente, 404 para ocultar un pago ajeno, 200 para lectura autorizada.

## Práctica ejecutada por el alumno

Desde Bash, en la raíz del repositorio:

```bash
mvn '-Dtest=ApiEndToEndTest#otherCustomersCannotReadPaymentButOwnerStillCan' test
mvn '-Dtest=AuthIntegrationTest#customerCannotAccessAdminEndpoint' test
```

| Prueba | Evidencia recibida en el chat | Qué comprueba |
|---|---|---|
| otherCustomersCannotReadPaymentButOwnerStillCan | Registro pegado: BUILD SUCCESS; 1 prueba, 0 fallos, 0 errores, 0 omitidas. Java 25.0.4.1; H2 en memoria. | Crea un pago, rechaza a otro cliente con 404, permite al dueño con 200 y devuelve 404 para un UUID inexistente. Usa HTTP real y el servidor de prueba. |
| customerCannotAccessAdminEndpoint | El alumno informó “Exitoso”; no se adjuntó el registro completo de esa ejecución. | Login de cliente y acceso administrativo rechazado con 403, mediante MockMvc y los filtros de seguridad. |

Fuentes de las pruebas: [ApiEndToEndTest](../src/test/java/com/securepay/integration/ApiEndToEndTest.java) y [AuthIntegrationTest](../src/test/java/com/securepay/security/AuthIntegrationTest.java).

No se modificaron clases ni pruebas. No se atribuyen al alumno ejecuciones realizadas antes por el asistente o por CI.

## Cobertura pendiente

- Añadir una prueba específica de GET de pago para admin sin CUSTOMER (403) y para usuario anónimo (401).
- Probar la matriz de forma simétrica: ambos clientes como dueños y como terceros.
- Probar un cuerpo de creación con `customerId` ajeno: si el campo se acepta/ignora, nunca debe cambiar el dueño autenticado; si se rechaza, no debe crearse el pago. La política de campos desconocidos no fue comprobada.
- Comparar respuestas de pago ajeno e inexistente para evitar diferencias que revelen su existencia.
- Revisar autorización comercial del merchant por separado; no queda demostrada por el control de dueño del pago.

Estos casos son trabajo pendiente, no vulnerabilidades confirmadas. Se acordará cualquier cambio de código antes de implementarlo.

## Continuidad

El día 24 se trabajó a continuación: [fallas criptográficas](day24-cryptographic-failures.md). El próximo tema es el día 25: prevención de inyección y pruebas.

Referencias: [mapa del día 22](day22-owasp-mapping.md), [OWASP A01:2021](https://top10.owasp.org/2021/A01_2021-Broken_Access_Control/).
