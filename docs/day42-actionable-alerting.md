# Día 42 — Alertas para un laboratorio local

## Objetivo

Practicar la detección de problemas y dejar una acción inicial para investigarlos. Los umbrales son ejemplos de aprendizaje: no son objetivos de producción ni sustituyen mediciones de tráfico reales.

## Diseño

El perfil adicional `monitoring` habilita `/actuator/prometheus` y un bucket de 500 ms para `http.server.requests`. El endpoint exige rol ADMIN; sin el perfil, no se expone por defecto. La configuración de laboratorio usa Prometheus en la misma red aislada de Compose, con un token Bearer en un archivo montado de solo lectura. El puerto de Prometheus se publica solo en loopback. La base temporal de Prometheus vive en memoria y se elimina al detener el contenedor; no sirve para evaluar un SLO de 30 días.

El archivo [alerts.yml](../monitoring/alerts.yml) define:

- `SecurePayScrapeFailed`: el scrape falla durante dos minutos. Puede indicar que la API no responde, un problema de red o que venció el token; no demuestra por sí solo una caída del servicio.
- `SecurePayHighServerErrorRate`: más del 5% de respuestas de pago son 5xx en cinco minutos, con al menos 20 solicitudes en esa ventana y la condición sostenida dos minutos.
- `SecurePaySlowPayments`: menos del 95% de respuestas 2xx de pago completan en 500 ms en cinco minutos, con al menos 20 respuestas 2xx y la condición sostenida dos minutos.

El mínimo de solicitudes evita alertas de proporciones con muestras pequeñas, pero también significa que **no** se alertará por estos porcentajes con tráfico bajo. La caída del proceso se observa mediante el scrape fallido; si se elimina el target de la configuración, `up` deja de existir y esta regla no lo detecta. Las métricas HTTP no incluyen fallos que ocurran antes de llegar a la aplicación. Cada alerta incluye una primera acción, no un diagnóstico definitivo.

La interfaz de Prometheus muestra alertas en estado pendiente o activo y las envía a Alertmanager, que las entrega a un receptor HTTP local de prueba. El receptor conserva solo nombre y estado en memoria y su puerto está limitado a loopback. No hay destinatarios, guardias ni notificaciones a personas o servicios externos.

Las imágenes oficiales de Alertmanager 0.34.1 y Python 3.14-alpine ensayadas inicialmente presentaron hallazgos HIGH corregibles en Trivy. El laboratorio ahora compila Alertmanager desde su código fuente fijado, con Go 1.27.3 y dependencias actualizadas y fijadas en `monitoring/alertmanager/go.mod` y `go.sum`; usa un receptor mínimo en Go en lugar de la imagen Python. La compilación es solo para el laboratorio: sustituye la interfaz web de Alertmanager por una página mínima y no incluye certificados de confianza para conexiones TLS externas.

## Práctica reproducible

Con PowerShell 7, Docker Desktop y el motor Docker funcionando:

```powershell
pwsh -NoProfile -File .\scripts\test-monitoring.ps1
docker run --rm --mount "type=bind,source=$((Get-Location).Path)\monitoring,target=/rules,readonly" --entrypoint /bin/promtool prom/prometheus:v3.15.1@sha256:fa3302279710be2f21a8301ec17a8b4f2301fd829bd113839b3b6b02b6f191a2 test rules /rules/alerts-test.yml
```

El script crea un proyecto Compose con nombre único, credenciales temporales y puertos aleatorios en loopback; obtiene un token de administrador para el scraper y comprueba el resultado `up=1` y el bucket de 500 ms. Luego detiene solo la API de ese proyecto y espera la entrega de `SecurePayScrapeFailed` al receptor local. Al terminar elimina sus contenedores, imágenes de prueba, volumen de base y archivo temporal del token. No inicia ni detiene proyectos Compose existentes. El token demo vence según la configuración JWT, por lo que una sesión de monitoreo prolongada requiere renovación segura.

Las pruebas de `promtool` usan series simuladas: comprueban que las tres alertas se activen y que no se activen con scrape sano y poco tráfico. No demuestran envío de notificaciones ni reproducen una carga real.

## Verificación local del 26 de octubre de 2026

- `mvn -B -ntp verify`: 122 casos contabilizados, 0 fallos, 0 errores y 5 omitidos por requerir PostgreSQL; SpotBugs sin hallazgos. Las pruebas nuevas verifican el control de acceso, la exposición por perfil y el bucket de latencia.
- `scripts/test-monitoring.ps1`: Prometheus 3.15.1 consultó el endpoint autenticado de la API y recibió un bucket de 500 ms en un proyecto Docker temporal con PostgreSQL.
- Tras detener la API de ese proyecto, `SecurePayScrapeFailed` pasó a `firing` y Alertmanager entregó la alerta al receptor local; el script terminó correctamente y eliminó los recursos temporales.
- `promtool test rules`: los escenarios simulados pasaron para las tres alertas.
- Trivy 0.76.1 sobre la imagen fijada de Prometheus 3.15.1: 0 hallazgos HIGH/CRITICAL con corrección disponible, según la base del escaneo. La imagen 3.5.0 inicialmente probada presentó hallazgos y se sustituyó.
- Trivy 0.76.1 sobre las imágenes reconstruidas de Alertmanager y del receptor: 0 hallazgos HIGH/CRITICAL con corrección disponible según la base utilizada. El escaneo no garantiza ausencia de otros riesgos.

Se agregaron pasos de CI para validar las reglas, construir y escanear las imágenes de monitoreo. La nueva configuración de CI se validará en GitHub cuando se proponga este cambio mediante PR.

## Antes de producción

Definir un scraper con identidad dedicada y rotación de credenciales, límites de red y TLS; elegir almacenamiento y retención duraderos; observar fallos externos a la aplicación; definir el tratamiento de bajo tráfico; acordar umbrales y ventanas con datos reales; configurar destinatarios, inhibición y un canal de notificación probado. El token demo de administrador y la memoria efímera de este laboratorio no resuelven esos requisitos.

Referencias: [métricas de Spring Boot](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [reglas de alerta de Prometheus](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/).
