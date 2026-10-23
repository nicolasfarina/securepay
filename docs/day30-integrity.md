# Día 30 — Integridad del software y de los datos

Fecha: 2026-10-23 (America/Buenos_Aires).

Revisión estática del flujo de cambios, construcción y datos. No se añadió integración de proveedor ni se verificaron firmas de eventos externos.

## Qué aprendemos

Integridad significa poder confiar en que el código, la configuración y los datos no fueron alterados sin autorización. No es lo mismo que confidencialidad: un JWT firmado puede detectar cambios, pero su payload sigue siendo legible. En una cadena de software, también importa quién puede cambiar dependencias, acciones de CI e imágenes.

## Evidencia observada

- `JwtService` firma tokens HMAC-SHA256 y verifica firma, encabezado y claims antes de crear autenticación.
- CI requiere revisión de pull request para proponer cambios, ejecuta pruebas y análisis, y limita permisos de workflow a lectura del contenido.
- Las referencias de acciones de Trivy y las imágenes Docker del Dockerfile y Compose están fijadas por digest. Algunas acciones de GitHub se referencian por tag; el tag no fija un contenido inmutable.
- Los archivos de pago validan datos entrantes y la persistencia usa migraciones Flyway. No hay callback público ni consumidor de eventos del proveedor en la API revisada.
- No hay deserialización polimórfica explícita ni carga de plugins identificada en el código raíz.

## Riesgos y límites

La verificación HMAC de JWT no demuestra integridad del flujo de pago completo. Cuando exista un webhook, el sistema tendrá que comprobar firma, timestamp/ventana de repetición, asociación al pago, duplicados y transición legal antes de persistir el resultado.

Fijar una referencia reduce cambios inesperados, pero no prueba por sí solo que una dependencia sea confiable. La documentación no revisó protecciones de rama, permisos de publicación, firma de artefactos ni provenance de una organización desplegada.

## Práctica pendiente

Diseñar una tabla de eventos de webhook con firma válida/inválida, evento repetido, evento tardío, pago inexistente y transición imposible; decidir qué evidencia se persiste para que la operación sea idempotente y auditable.

## Fuentes

- [OWASP A08:2021 — Software and Data Integrity Failures](https://owasp.org/Top10/en/A08_2021-Software_and_Data_Integrity_Failures/)
- Código y configuración revisados: `JwtService.java`, `PaymentStatus.java`, `PaymentCreationTransaction.java`, `pom.xml`, `.github/workflows/ci.yml` y `Dockerfile`.
