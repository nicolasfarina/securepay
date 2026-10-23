# Día 32 — SSRF y controles de llamadas salientes

Fecha: 2026-10-23 (America/Buenos_Aires).

Revisión de superficie de red del código raíz. No se ejecutó un escaneo de red ni existe un cliente de proveedor que permita probar SSRF.

## Qué aprendemos

SSRF ocurre cuando el servidor hace una solicitud a un destino que el atacante puede influir. El riesgo aparece al aceptar URLs o destinos externos y hacer que la aplicación los consulte. Validar una URL con una lista de bloqueo simple no basta: redirecciones y cambios DNS pueden convertir un destino aparentemente válido en uno interno.

## Evidencia observada

- La búsqueda del código productivo no encontró cliente HTTP, descargador, URL controlada por usuario ni endpoint que obtenga una URL remota.
- Las llamadas HTTP encontradas están en pruebas y apuntan a `127.0.0.1` para hablar con la aplicación bajo prueba.
- Compose permite a la API hablar con PostgreSQL en la red interna; el código de aplicación no realiza otra llamada saliente observada.
- No hay integración de proveedor, webhook entrante ni consumo de API externa en la implementación raíz.

## Evaluación y límites

No se identificó una ruta SSRF actual en el código revisado. Eso no significa que la infraestructura bloquee destinos internos ni que futuras integraciones sean seguras. El modelo de amenazas menciona proveedor externo como arquitectura futura y requiere controles antes de agregar esa función.

## Práctica pendiente antes del proveedor

Evitar URLs arbitrarias del usuario. Si el destino es configurable, permitir solo hosts y esquemas aprobados, desactivar redirecciones, validar IP resuelta y revalidarla al conectar, bloquear loopback/redes privadas/link-local/metadata, fijar timeouts y aplicar egreso de red por defecto-denegado. No devolver la respuesta cruda del servidor externo al cliente.

## Fuentes

- [OWASP A10:2021 — Server-Side Request Forgery](https://owasp.org/Top10/en/A10_2021-Server-Side_Request_Forgery_%28SSRF%29/)
- [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
- Alcance de búsqueda: código bajo `src/main/java` y configuración Compose.
