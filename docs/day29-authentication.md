# Día 29 — Fallas de autenticación y seguridad de tokens

Fecha: 2026-10-23 (America/Buenos_Aires).

Revisión de diseño y lectura de pruebas. No se ejecutó una evaluación de fuerza bruta ni se cambió la autenticación.

## Qué aprendemos

Autenticación responde «quién sos»; autorización decide «qué podés hacer». Un JWT firmado protege integridad, pero no cifra su contenido. La vida corta reduce la ventana de uso, aunque no reemplaza revocación, rotación ni límites contra intentos automáticos.

## Evidencia observada

- `/auth/login` usa `AuthenticationManager`, exige campos no vacíos y devuelve el mismo mensaje para credenciales inválidas.
- Las contraseñas de las cuentas demo se codifican con BCrypt. Las cuentas viven en memoria y usan una contraseña común inyectada por configuración.
- El token se firma con HS256; el código exige el encabezado exacto, compara firmas en tiempo constante y valida emisor, sujeto, rol contra la cuenta activa y expiración.
- El token expira por defecto en una hora. La longitud mínima de la clave es 32 caracteres.
- Las pruebas de autenticación e integración existentes cubren login, tokens inválidos/alterados, vencimiento y controles de acceso documentados en Días 23–24.

## Riesgos y límites

El perfil local tiene credenciales de demostración predeterminadas. No hay limitación de intentos en login, MFA, ciclo de recuperación de cuenta, rotación administrada de claves ni revocación anticipada de tokens. La ausencia de esos controles es relevante para producción, aunque las cuentas son de demostración y no existe un servicio de identidad persistente.

La implementación JWT es propia y pequeña; las pruebas actuales no sustituyen una revisión de protocolo y biblioteca. No afirmamos que SecurePay sea apto para autenticar clientes reales.

## Práctica pendiente

Comparar el comportamiento de un token alterado con el de uno expirado: ambos deben fallar la autenticación, aunque por causas distintas. Luego diseñar qué componente protegería `/auth/login` de intentos automatizados y cómo evitaría bloquear a clientes legítimos.

## Fuentes

- [OWASP A07:2021 — Identification and Authentication Failures](https://owasp.org/Top10/en/A07_2021-Identification_and_Authentication_Failures/)
- Código revisado: `AuthController.java`, `SecurityConfig.java`, `JwtService.java`, `JwtAuthenticationFilter.java` y `AuthIntegrationTest.java`.
