# Día 35 — Pruebas negativas de seguridad de API

Fecha: 2026-10-24 (America/Argentina/Cordoba).

**Estado:** completado el 2026-10-24; seis casos negativos incorporados a `main` a través de [PR #36](https://github.com/nicolasfarina/securepay/pull/36), fusionado primero en la rama del parche de Brotli de [PR #35](https://github.com/nicolasfarina/securepay/pull/35), después fusionada a `main`. CI de `main` aprobado: [run #40109195344](https://github.com/nicolasfarina/securepay/actions/runs/40109195344).

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

## Evidencia de ejecución, CI y merge

- GitHub Actions del [PR #36, CI #40108106256](https://github.com/nicolasfarina/securepay/actions/runs/40108106256): `ApiNegativeSecurityTest` — **6 casos, 0 fallos, 0 errores, 0 omitidos**.
- Suite general Maven `verify`: **74 casos contabilizados, 69 pasados, 5 omitidos** (los cinco requieren PostgreSQL), SpotBugs sin hallazgos bloqueantes. El job concluyó en éxito.
- Suite PostgreSQL seleccionada: **49 tests pasados, 0 fallos, 0 errores, 0 omitidos**, en el mismo job.
- Al integrar el parche de [Brotli CVE-2026-111497, PR #35](https://github.com/nicolasfarina/securepay/pull/35), `main` quedó en el commit `16f5f8b6b85356d25cbc474b6075ff4219882a15`; su [CI #40109195344](https://github.com/nicolasfarina/securepay/actions/runs/40109195344) terminó **success** con Trivy, compilación, tests Maven y PostgreSQL aprobados.
- [Secret Scan #40109195350](https://github.com/nicolasfarina/securepay/actions/runs/40109195350) y [Publish container image #40109195347](https://github.com/nicolasfarina/securepay/actions/runs/40109195347) concluyeron en éxito en el mismo commit de `main`.
- Los tests son del laboratorio automatizado del repositorio; estas ejecuciones son evidencia de CI, no de ejecución personal del alumno ni una auditoría de producción.

## Ejecutar

```bash
mvn -B -ntp -Dtest=ApiNegativeSecurityTest test
mvn -B -ntp verify
```

## Limitaciones y criterios de cierre

- Cambiar `X-Forwarded-For` sin cambiar `remoteAddr` prueba el comportamiento **actual** en MockMvc. No demuestra que la configuración de un reverse proxy real sea segura; el proxy debe eliminar encabezados de clientes no confiables y definir qué remotos se consideran trusted proxies.
- La respuesta 403 demuestra autorización denegada a través del endpoint probado. No sustituye una prueba de efectos sobre la base de datos para operaciones que mutan datos.
- Los resultados de CI verifican las rutas y condiciones descritas, no todas las variantes posibles de encabezados ni una configuración real de reverse proxy. Mantener este límite explícito al interpretar los tests.
- La corrección de Trivy (CVE-2026-111497) fue incorporada en el PR #35 y validada en la imagen; el cierre del Día 35 no modifica la configuración de producción.
