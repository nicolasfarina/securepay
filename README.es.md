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

## Seguridad

SecurePay seguirá prácticas de seguridad basadas en:

- OWASP Top 10
- OWASP API Security Top 10
- Buenas prácticas de seguridad en la nube
