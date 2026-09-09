# SecurePay

SecurePay is a secure payment platform built as a DevSecOps and Cloud Security learning project.

## Goal

The goal of SecurePay is to create a secure, observable, and scalable payment platform using modern software engineering practices.

## Main Features

- Credit card payments
- Secure payment processing
- Payment status management
- Idempotent payment requests
- Authentication and authorization
- Observability
- Cloud infrastructure
- DevSecOps security controls

## Payment Status

A payment can have the following status:

- `PENDING` — The payment is being processed.
- `APPROVED` — The payment was completed successfully.
- `REJECTED` — The payment was not approved.
- `UNKNOWN` — The final result is not yet known.
- `REFUNDED` — The approved payment was returned.

## Technologies

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

## Security

SecurePay will follow security practices based on:

- OWASP Top 10
- OWASP API Security Top 10
- Cloud Security best practices
