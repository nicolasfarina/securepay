# SecurePay

SecurePay es una plataforma de pagos segura creada como proyecto de aprendizaje de DevSecOps y Seguridad en la Nube.

## Objetivo

El objetivo de SecurePay es crear una plataforma de pagos segura, observable y escalable usando prácticas modernas de ingeniería de software.

## Funcionalidades principales

- Pagos con tarjeta de crédito
- Procesamiento seguro de pagos
- Gestión de estados de pago
- Solicitudes de pago idempotentes
- Autenticación y autorización
- Observabilidad
- Infraestructura en la nube
- Controles de seguridad DevSecOps

## Estados de pago

Un pago puede tener los siguientes estados:

- `PENDING` — El pago está siendo procesado.
- `APPROVED` — El pago fue completado correctamente.
- `REJECTED` — El pago no fue aprobado.
- `UNKNOWN` — El resultado final todavía no se conoce.
- `REFUNDED` — El pago aprobado fue devuelto.

## Tecnologías

- Java
- Spring Boot
- PostgreSQL
- Docker
- Kubernetes
- Terraform
- Terragrunt
- Google Cloud Platform
- OpenTelemetry
- GitHub Actions

## Ejecutar localmente

SecurePay requiere Java 25 y Maven. Para usar la base de datos en memoria predeterminada, iniciar la API con:

```shell
mvn spring-boot:run
```

Ejecutar la suite de pruebas con:

```shell
mvn test
```

Registrar una cuenta e iniciar sesión para obtener un JWT:

```shell
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"una-clave-local-larga"}'

curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"una-clave-local-larga"}'
```

Enviar el token devuelto como `Authorization: Bearer <token>` a los endpoints protegidos. El registro público siempre crea un usuario `CUSTOMER`; los roles administrativos deben asignarse mediante un proceso administrativo confiable. Las contraseñas se almacenan con BCrypt. El repositorio actual de cuentas vive en memoria: las cuentas se pierden al reiniciar la aplicación y no se comparten entre instancias.

Configurar `SECUREPAY_JWT_SECRET` con una clave única de al menos 32 bytes en cada entorno desplegado. La clave predeterminada sólo es para desarrollo local. Los tokens vencen en una hora por defecto; cambiarlo con `SECUREPAY_JWT_EXPIRATION_SECONDS`.

Los datos de pagos e idempotencia se almacenan mediante JPA y se administran con migraciones Flyway. El perfil local/de prueba predeterminado usa una base de datos en memoria; el perfil `prod` requiere los valores de conexión PostgreSQL `SECUREPAY_DATABASE_URL`, `SECUREPAY_DATABASE_USERNAME` y `SECUREPAY_DATABASE_PASSWORD`.

El endpoint de salud está disponible en `http://localhost:8080/actuator/health`. Informa el estado general de la aplicación y de la base de datos sin exponer detalles de componentes. Los probes de disponibilidad y actividad están en `/actuator/health/readiness` y `/actuator/health/liveness`.

La documentación interactiva de la API está disponible localmente en `http://localhost:8080/swagger-ui.html`; el documento OpenAPI está en `/v3/api-docs`. Swagger UI queda deshabilitado con el perfil `prod`. En la interfaz, registrarse o iniciar sesión y luego usar **Authorize** con el token bearer devuelto para probar los endpoints de pagos protegidos.

### Ejecutar la API y PostgreSQL con Docker

Con Docker Desktop en ejecución, inicia ambos servicios desde PowerShell:

```powershell
.\scripts\run-local.ps1
```

El script construye la imagen de la API con Java 25, inicia PostgreSQL, espera la comprobación de disponibilidad de la API y muestra las direcciones locales. Detén los contenedores con `docker compose down`. Si el puerto 8080 ya está ocupado, configura `SECUREPAY_API_PORT` antes de ejecutar el script para elegir otro. También puedes cambiar la contraseña de la base de datos y la clave de firma JWT locales con `SECUREPAY_DATABASE_PASSWORD` y `SECUREPAY_JWT_SECRET`.

### Ejecutar con PostgreSQL

Docker Compose inicia una base PostgreSQL local con un volumen persistente. Iniciarla con:

```shell
docker compose up -d postgres
```

Luego ejecutar SecurePay con el perfil `postgres` para que Flyway aplique las migraciones en PostgreSQL:

```shell
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

La base local sólo queda disponible en `127.0.0.1:5432`. La contraseña predeterminada es sólo para desarrollo local; configurar `SECUREPAY_DATABASE_PASSWORD` antes de iniciar Compose para cambiarla. Detener la base con `docker compose down`; los datos permanecen en el volumen de Compose.

## Seguridad

SecurePay seguirá prácticas de seguridad basadas en:

- OWASP Top 10
- OWASP API Security Top 10
- Buenas prácticas de seguridad en la nube

Artefactos de planificación de seguridad:

- [Modelo de amenazas del flujo de pagos](security/threat-models/securepay-payment-flow.es.md)
- [Registro de riesgos de seguridad](security/risk-register.es.md)
- [Plan de verificación de seguridad](security/security-verification-plan.es.md)
