# Día 35 — Pruebas negativas de seguridad de API

Fecha: 2026-10-24 (America/Argentina/Cordoba).

**Estado:** pruebas propuestas en una rama; no marcar como verificadas hasta revisar CI. El trabajo depende del parche de Brotli en PR #35. No se ha hecho merge a main.

## Alcance incremental

La base existente ya incluye cobertura para SQL-looking inputs (Día 25), segregación por dueño y rol (Día 23), JWT manipulado o expirado (Día 24), y rate limiting básico (Día 34). En este día evitamos repetir las mismas pruebas y agregamos casos negativos en los límites de confianza de encabezados HTTP.

La clase `ApiNegativeSecurityTest` usa la aplicación real con MockMvc y los filtros de JWT y rate limiting.

| Prueba | Riesgo evaluado | Respuesta esperada |
|---|---|---|
| `spoofedForwardedForCannotBypassLoginRateLimit` | Un cliente rota `X-Forwarded-For` para evadir la cuota asociada a la IP real del socket. | Las primeras dos credenciales inválidas producen 401; la tercera produce 429 y `Retry-After`. |
| `spoofedIdentityHeadersCannotPromoteCustomerToAdmin` | Un CUSTOMER intenta declararse ADMIN con encabezados arbitrarios. | 403, pese al JWT válido de CUSTOMER. |
| `spoofedIdentityHeadersCannotAuthenticateWithoutJwt` | Un cliente envía encabezados de identidad sin JWT. | 401. |
| `unsupportedOrInvalidAuthorizationHeadersAreRejected` | Esquemas Basic, Token y Bearer inválido contra una ruta administrativa. | 401 para cada caso. |

Los rangos `198.51.100.0/24` y `203.0.113.0/24` usados en tests son direcciones de documentación, no sistemas externos. MockMvc simula `remoteAddr` sin generar solicitudes de red a esos destinos.

## Ejecutar

```bash
mvn -B -ntp -Dtest=ApiNegativeSecurityTest test
mvn -B -ntp verify
```

## Limitaciones y criterios de cierre

- Cambiar `X-Forwarded-For` sin cambiar `remoteAddr` prueba el comportamiento **actual** en MockMvc. No demuestra que la configuración de un reverse proxy real sea segura; el proxy debe eliminar encabezados de clientes no confiables y definir qué remotos se consideran trusted proxies.
- La respuesta 403 demuestra autorización denegada a través del endpoint probado. No sustituye una prueba de efectos sobre la base de datos para operaciones que mutan datos.
- Los códigos esperados no equivalen a resultados ejecutados. Revisar y registrar la suite Maven y los resultados del pipeline antes del cierre.
- El desbloqueo de Trivy (CVE-2026-111497) se gestiona en el PR #35, separado de la cobertura de este día.
