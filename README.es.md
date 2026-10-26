# SecurePay

SecurePay es una plataforma de pagos segura creada como proyecto de aprendizaje e ingeniería de referencia en DevSecOps y Seguridad en la Nube.

## Progreso técnico — Días 22 a 41 completados

- **Días 22–25 (OWASP Top 10, Control de Acceso, Criptografía e Inyección):** Mapeo completo de OWASP Top 10:2021 (con referencia a 2025), matriz de autorización HTTP (`401` sin autenticar, `403` rol insuficiente, `404` sobre pagos de otro cliente para evitar enumeración, `200` para el titular), validación de firma JWT HS256 ante alteración de payload y pruebas de parametrización SQL frente a literales maliciosos. Ver [Día 22](docs/day22-owasp-mapping.md), [Día 23](docs/day23-access-control.md), [Día 24](docs/day24-cryptographic-failures.md) y [Día 25](docs/day25-injection.md).
- **Días 26–33 (Revisiones de Diseño y OWASP API Security Top 10):** Revisiones documentales con evidencia en código sobre [diseño inseguro](docs/day26-insecure-design.md), [configuración de seguridad](docs/day27-security-misconfiguration.md), [riesgo de componentes](docs/day28-vulnerable-components.md), [autenticación](docs/day29-authentication.md), [integridad](docs/day30-integrity.md), [logging y monitoreo](docs/day31-logging-monitoring.md), [SSRF](docs/day32-ssrf.md) y [OWASP API Security Top 10](docs/day33-api-top10.md).
- **Días 34–35 (Rate Limiting y Pruebas Negativas de Seguridad HTTP):** Implementación de limitación de tasa con *token bucket* configurable por IP en `/auth/login` y por cliente autenticado en `/api/payments` (`429 Too Many Requests` con `Retry-After`), más suite de pruebas negativas frente a spoofing de `X-Forwarded-For`, cabeceras de identidad falsificadas (`X-Role`, `X-Authenticated-User`) y esquemas de autorización no soportados. Ver [Día 34](docs/day34-rate-limiting.md) y [Día 35](docs/day35-api-negative-security-tests.md).
- **Días 36–41 (Observabilidad, OpenTelemetry, Resiliencia y SLOs):** Logs estructurados en JSON con validación de `X-Correlation-Id` y propagación MDC, métricas Micrometer de transiciones de pago restringidas al rol `ADMIN`, spans manuales con OpenTelemetry SDK, propagación W3C Trace Context en `RestClient` y ejecutores asíncronos, verificación de *readiness/liveness* ante caída y recuperación de PostgreSQL 18.6, y definición de SLIs/SLOs ([Día 36](docs/day36-structured-logging.md), [Día 37](docs/day37-metrics.md), [Día 38](docs/day38-opentelemetry-fundamentals.md), [Día 39](docs/day39-distributed-tracing.md), [Día 40](docs/day40-health-probes.md) y [Día 41](docs/day41-sli-slo.md)).
- **Verificación automatizada:** `scripts/test-integration.ps1` aprueba con **115+ pruebas automatizadas (0 fallos, 0 errores)** incluyendo pruebas reales sobre PostgreSQL 18.6 y Tomcat, más análisis estático con SpotBugs/FindSecBugs sin hallazgos.

## Objetivo

El objetivo de SecurePay es crear una plataforma de pagos segura, observable y escalable usando prácticas modernas de ingeniería de software.

## Funcionalidades principales

- Pagos con tarjeta de crédito
- Procesamiento seguro de pagos
- Gestión de estados de pago
- Solicitudes de pago idempotentes (`Idempotency-Key` + huella SHA-256)
- Autenticación con Bearer JWT firmado (HS256) y autorización por rol y titularidad
- Rate limiting en autenticación y creación de pagos
- Observabilidad (JSON logs, Correlation ID, Micrometer y OpenTelemetry)
- Controles de seguridad DevSecOps en CI/CD

## Estados de pago

Un pago puede tener los siguientes estados:

- `PENDING` — El pago está siendo procesado.
- `APPROVED` — El pago fue completado correctamente.
- `REJECTED` — El pago no fue aprobado.
- `UNKNOWN` — El resultado final todavía no se conoce.
- `REFUNDED` — El pago aprobado fue devuelto.

## Tecnologías

- Java 25
- Spring Boot 4
- PostgreSQL 18
- Flyway
- Docker
- OpenTelemetry & Micrometer
- GitHub Actions (Gitleaks, SpotBugs/FindSecBugs, Trivy, CycloneDX SBOM)

## Ejecutar localmente

SecurePay requiere Java 25 y Maven. El perfil predeterminado `local` usa una base de datos H2 en memoria y credenciales demo exclusivamente locales. Inicia la API con:

```shell
mvn spring-boot:run
```

Ejecuta la suite de pruebas con:

```shell
mvn test
```

Para ejecutar la verificación completa de integración con una instancia descartable de PostgreSQL en Docker (requiere PowerShell y Docker Desktop activo):

```powershell
.\scripts\test-integration.ps1
```

Autentícate en `/auth/login` para obtener un token JWT:

```shell
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"customer-123","password":"<local-demo-password>"}'
```

Reemplaza `<local-demo-password>` por la contraseña configurada en tu entorno local antes de ejecutar la solicitud. Nunca subas credenciales reales al repositorio.

Usa el `accessToken` devuelto como token Bearer para crear un pago con clave de idempotencia:

```shell
curl -X POST http://localhost:8080/api/payments \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: example-payment-001" \
  -d '{"merchantId":"merchant-456","amount":10.00,"currency":"USD"}'
```

Los usuarios demo locales son `customer-123`, `customer-789` y `admin-123`. Las contraseñas se almacenan en memoria codificadas con BCrypt y los JWT se firman con HS256. La clave de firma predeterminada es sólo para desarrollo local; el perfil `prod` exige `SECUREPAY_JWT_SECRET` explícito y en despliegues productivos debe integrarse un proveedor de identidad externo.

Los datos de pagos e idempotencia se almacenan mediante JPA y se administran con migraciones Flyway. La configuración se selecciona mediante perfiles de Spring:

| Perfil | Base de datos | Credenciales y uso previsto |
|---|---|---|
| `local` (predeterminado) | H2 en memoria | Valores locales de aprendizaje. Configura `SECUREPAY_DEMO_PASSWORD` y `SECUREPAY_JWT_SECRET` para sobrescribir las credenciales demo. |
| `postgres` | PostgreSQL | Desarrollo local contra PostgreSQL (`localhost:5432/securepay` por defecto); configura `SECUREPAY_DATABASE_PASSWORD` y `SECUREPAY_JWT_SECRET`. |
| `prod` | PostgreSQL | Perfil de despliegue. Requiere `SECUREPAY_DATABASE_URL`, `SECUREPAY_DATABASE_USERNAME`, `SECUREPAY_DATABASE_PASSWORD`, `SECUREPAY_DEMO_PASSWORD` y `SECUREPAY_JWT_SECRET`; Swagger/OpenAPI quedan deshabilitados y bloqueados en `SecurityConfig`. |

Por ejemplo, para ejecutar contra una instancia PostgreSQL local en PowerShell:

```powershell
$env:SECUREPAY_DATABASE_PASSWORD = 'your-local-database-password'
$env:SECUREPAY_JWT_SECRET = 'replace-with-a-random-secret-at-least-32-characters'
mvn spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

El endpoint de salud está disponible en `http://localhost:8080/actuator/health` e informa el estado general sin exponer detalles internos. Los probes de disponibilidad y actividad están en `/actuator/health/readiness` y `/actuator/health/liveness`.

La documentación interactiva de la API está disponible localmente en `http://localhost:8080/swagger-ui.html` y el esquema OpenAPI en `/v3/api-docs`. En el perfil `prod`, tanto Swagger UI como `/v3/api-docs` están deshabilitados y protegidos por `SecurityConfig`. En la interfaz local, invoca `/auth/login`, copia el JWT devuelto y usa **Authorize** con el token Bearer.

### Ejecutar la API y PostgreSQL con Docker

El `Dockerfile` compila y ejecuta las pruebas con Maven sobre JDK 26 apuntando a Java 25, y luego copia el JAR ejecutable a una imagen runtime Java 25 separada que corre como usuario no-root (`UID/GID 10001`), con imágenes base fijadas por digest SHA-256.

Para construir la imagen y verificarla contra un contenedor PostgreSQL aislado (requiere PowerShell 7 y Docker Desktop):

```powershell
.\scripts\test-container.ps1
```

Con Docker Desktop en ejecución, inicia ambos servicios desde PowerShell:

```powershell
.\scripts\run-local.ps1
```

Desde Bash (Linux, WSL con acceso a Docker o Git Bash), usa:

```bash
bash scripts/run-local.sh
```

La versión Bash genera contraseñas locales aleatorias para PostgreSQL y los usuarios demo, además de una clave de firma JWT en `.env.securepay-local` en el primer arranque, y reutiliza ese archivo en ejecuciones posteriores.

Para verificar resiliencia ante caídas de base de datos, persistencia entre recreaciones y probes de salud (Día 13 / Día 40):

```powershell
.\scripts\test-compose.ps1
```

### Ejecutar contra PostgreSQL desde el host

Por seguridad, `compose.yaml` no publica el puerto de PostgreSQL en el host por defecto. Para exponer PostgreSQL en loopback (`127.0.0.1:5432`) cuando ejecutes Java o un cliente SQL en el host, utiliza el archivo de override opcional:

```shell
docker compose -f compose.yaml -f compose.host-db.yaml up -d --wait postgres
```

Luego inicia SecurePay con el perfil `postgres` para que Flyway aplique las migraciones sobre PostgreSQL:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Para detenerlo conservando los datos en el volumen de Compose:

```shell
docker compose -f compose.yaml -f compose.host-db.yaml down
```

## Seguridad

SecurePay aplica controles basados en:

- OWASP Top 10
- OWASP API Security Top 10
- Buenas prácticas de seguridad en contenedores y nube

Artefactos de planificación y verificación de seguridad:

- [Modelo de amenazas del flujo de pagos](security/threat-models/securepay-payment-flow.es.md)
- [Registro de riesgos de seguridad](security/risk-register.es.md)
- [Plan de verificación de seguridad](security/security-verification-plan.es.md)

Mantén las credenciales locales en variables de entorno del proceso o en un archivo `.env` ignorado por Git; nunca las subas al repositorio. El pipeline de CI ejecuta Gitleaks en cada PR y push, además de un escaneo programado semanal sobre todo el historial de Git.
