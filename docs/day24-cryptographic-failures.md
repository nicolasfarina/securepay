# Día 24 — Fallas criptográficas

Fecha de la sesión: 2026-10-21 (America/Buenos_Aires). Lección conceptual y práctica guiada completadas en español.

Código revisado para este registro: `a9cc7072cb52b5ae0afe00848d23e0abb76c2fca`. Las pruebas informadas por el alumno se ejecutaron en su copia local; no se verificó su SHA.

## Objetivo y alcance

Distinguir hash de contraseñas, autenticación criptográfica del JWT, cifrado en tránsito y cifrado en reposo. Se revisaron controles existentes y se ejecutaron pruebas existentes. Completar la lección no equivale a verificar la seguridad criptográfica de un despliegue de producción.

El roadmap usa A02:2021 (Cryptographic Failures); su correspondencia en OWASP 2025 es A04.

## Lo aprendido y lo observado

| Tema | Conclusión | Evidencia o límite |
|---|---|---|
| Contraseñas | BCrypt genera un hash que no se descifra con una clave. Un atacante todavía puede probar contraseñas candidatas; el costo de BCrypt dificulta esos intentos. | [SecurityConfig](../src/main/java/com/securepay/security/SecurityConfig.java) usa BCryptPasswordEncoder y usuarios demo en memoria. No se hizo una prueba dedicada de costo, salt o política de contraseñas. |
| JWT legible | Base64URL es codificación, no cifrado. Se pueden leer usuario, rol y vencimiento sin la clave. No deben incluirse contraseñas ni secretos. | [JwtService](../src/main/java/com/securepay/security/JwtService.java) construye un JWT firmado con HS256. La prueba de login decodifica su payload. |
| Integridad del JWT | Cambiar el payload sin recalcular correctamente su firma debe invalidar el token. HS256 usa autenticación mediante una clave compartida. | JwtService verifica el HMAC con MessageDigest.isEqual. La sesión probó firma con otra clave y otros tokens inválidos; no una edición manual del payload de un token válido. |
| Clave de firma | Conocer la clave permitiría crear firmas válidas. El valor real no debe subirse a Git, incluso si el repositorio es privado. | [application-prod.yml](../src/main/resources/application-prod.yml) referencia SECUREPAY_JWT_SECRET, no un valor real. La referencia a una variable no demuestra uso de un gestor de secretos. |
| Rotación | Borrar un secreto del último commit no invalida las copias. Una filtración exige reemplazarlo y dejar de confiar en la clave anterior. | Concepto revisado; no se ejecutó una rotación ni se probó un mecanismo con múltiples claves. |
| Transporte | HTTPS cifra la comunicación. La firma de un JWT no impide que un tercero robe y reutilice un Bearer token interceptado. | Las pruebas locales usan HTTP. No demuestran HTTPS, certificados ni TLS de producción. |
| Datos almacenados | HTTPS no cifra automáticamente PostgreSQL, sus discos ni sus copias de seguridad. | Cifrado en reposo, permisos de claves y backups quedan pendientes de verificación en el despliegue. |

## Práctica 1 — Leer el JWT sin exponer la contraseña

```bash
mvn '-Dtest=AuthIntegrationTest#loginReturnsBearerJwtWithoutPasswordClaims' test
```

El alumno informó “Dio ok”. No se adjuntó el registro completo ni un conteo para esa ejecución.

La [prueba](../src/test/java/com/securepay/security/AuthIntegrationTest.java) hace login, comprueba Bearer y las tres partes del JWT, decodifica el payload sin la clave, comprueba el usuario y rol esperados y que no contenga la palabra `password`. Ese assert no demuestra por sí solo la ausencia de todo dato sensible ni una validación completa del JWT.

## Práctica 2 — Rechazar tokens inválidos

```bash
mvn '-Dtest=ApiEndToEndTest#invalidTokensCannotCreatePaymentsOrReadAdminData' test
```

Resultado pegado por el alumno:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

La [prueba parametrizada](../src/test/java/com/securepay/integration/ApiEndToEndTest.java) cubre seis entradas:

1. Token malformado.
2. Token vacío.
3. Token vencido.
4. Token firmado con otra clave.
5. Token firmado con la clave de prueba, pero con rol ADMIN para un usuario que es CUSTOMER.
6. Token firmado con la clave de prueba para un usuario desconocido.

Para cada entrada verifica 401 al crear pagos y al consultar el listado administrativo, y comprueba que no se hayan escrito pagos ni registros de idempotencia. Son seis casos parametrizados, no seis causas puramente criptográficas: también se verifican identidad y coherencia del rol.

[JwtAuthenticationFilter](../src/main/java/com/securepay/security/JwtAuthenticationFilter.java) valida el token, carga al usuario conocido y habilitado y compara su rol. Si la autenticación no se establece, la ruta protegida devuelve 401.

El resumen muestra que los seis casos pasaron; no se recibió el log completo de Maven de esta ejecución. Los resultados del alumno se mantienen separados de las comprobaciones del asistente y de CI.

## Pendientes concretos

- Verificar TLS de la API y de las conexiones a PostgreSQL según el entorno.
- Verificar cifrado de almacenamiento y backups y permisos de acceso a claves.
- Diseñar y ensayar gestión/rotación de claves; la longitud mínima de 32 caracteres comprobada por JwtService no garantiza aleatoriedad.
- Revisar el parser JWT propio y evaluar una biblioteca mantenida o un proveedor de identidad.
- Añadir una prueba explícita de payload alterado conservando la firma original.
- Revisar política y costo del hash de contraseñas. Los usuarios demo en memoria no son un sistema de identidad de producción.

No se implementaron controles nuevos ni se modificó código para esta lección. Los pendientes no se presentan como resueltos.

## Próxima sesión — Día 25: prevención de inyección y pruebas

Planificado para el 2026-10-22, por pedido del alumno; no es una automatización.

1. Distinguir validación de entrada de prevención de inyección.
2. Revisar campos de CreatePaymentRequest y el recorrido hasta los repositorios JPA.
3. Revisar consultas parametrizadas y cualquier consulta SQL/JPQL construida manualmente.
4. Definir casos negativos seguros en el entorno de pruebas y comprobar que no haya operaciones ni datos indebidos.
5. Acordar cualquier cambio antes de editar código y registrar resultados.

Continuar en español, paso a paso. Aún no se marca el día 25 como completado.

## Referencias

- [Día 23 — Control de acceso](day23-access-control.md)
- [OWASP — Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Mapa OWASP y correspondencia 2021/2025](day22-owasp-mapping.md)
