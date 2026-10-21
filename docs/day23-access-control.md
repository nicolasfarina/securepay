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

En la práctica inicial no se modificaron clases ni pruebas. No se atribuyen al alumno ejecuciones realizadas por el asistente o por CI. La ampliación autorizada posteriormente se registra a continuación.

## Ampliación de cobertura — 2026-10-21

El alumno autorizó cerrar los casos de aplicación antes de avanzar al día 25. El asistente agregó pruebas HTTP reales en [ApiEndToEndTest](../src/test/java/com/securepay/integration/ApiEndToEndTest.java), conservando la lógica de producción:

| Prueba | Casos nuevos | Verificación |
|---|---|---|
| paymentReadAccessMatrixEnforcesRoleAndOwnership | 2, alternando ambos clientes como dueños | Sobre un pago existente: anónimo 401, admin sin CUSTOMER 403, dueño 200 y otro cliente 404. Las denegaciones no incluyen datos del pago. |
| suppliedCustomerIdCannotOverrideAuthenticatedOwner | 2, alternando ambos clientes como autores | Un `customerId` ajeno en el cuerpo se ignora: respuesta 201 y dueño igual al usuario autenticado, tanto en respuesta como en base de datos. El dueño puede leerlo y el cliente indicado en el cuerpo recibe 404. |

La prueba de lectura compara además el Content-Type y el JSON completo de un pago ajeno con los de uno inexistente, normalizando únicamente el UUID solicitado. Esto comprueba la forma pública del error; no es un análisis de tiempos ni de todos los canales laterales.

Comando reproducible en Bash:

```bash
mvn '-Dtest=ApiEndToEndTest#paymentReadAccessMatrixEnforcesRoleAndOwnership+suppliedCustomerIdCannotOverrideAuthenticatedOwner' test
```

La verificación completa ejecutada por el asistente con Java 25 y H2 fue `mvn -B -ntp verify`: **BUILD SUCCESS; 61 casos contabilizados, 0 fallos, 0 errores y 5 omitidos**. Son **56 aprobados**, incluidos estos 4 casos y el nuevo caso de JWT del día 24. Los 5 omitidos requieren PostgreSQL; no se presentan como aprobados localmente. SpotBugs terminó sin hallazgos. La suite HTTP pasó sus 41 casos.

## Pendientes de alcance posterior

- Revisar autorización comercial del merchant por separado, junto con diseño y reglas de negocio; no queda demostrada por el control de dueño del pago.
- Ampliar el análisis de canales laterales si cambia el modelo de amenazas.

Los huecos de cobertura de aplicación enumerados en la versión inicial de esta lección quedan cubiertos por estas pruebas. Esto no certifica toda la seguridad del servicio.

## Continuidad

El día 24 se trabajó a continuación: [fallas criptográficas](day24-cryptographic-failures.md). El próximo tema es el día 25: prevención de inyección y pruebas.

Referencias: [mapa del día 22](day22-owasp-mapping.md), [OWASP A01:2021](https://top10.owasp.org/2021/A01_2021-Broken_Access_Control/).
