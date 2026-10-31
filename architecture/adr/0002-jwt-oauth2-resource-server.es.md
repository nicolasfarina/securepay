# ADR 0002: Validación JWT con Spring OAuth2 Resource Server

English version: [Read in English](0002-jwt-oauth2-resource-server.md)

## Estado

Propuesta; implementada en la rama del PR, pendiente de revisión y aprobación explícita del merge.

## Contexto

La aplicación raíz generaba y analizaba JWT HS256 con código propio y expresiones regulares.
Una firma válida no debe dar acceso a una cuenta desconocida, deshabilitada o con otro rol.
La propiedad de pagos y el límite por usuario dependen del nombre de la cuenta autenticada.

## Decisión

Usar el starter administrado por Spring Boot 4.1.2
`spring-boot-starter-security-oauth2-resource-server`, NimbusJwtEncoder para emitir y
NimbusJwtDecoder para verificar. Eliminar el filtro y parser propios. Conservar login,
configuración del secreto HS256, emisor `securepay`, claim role y duración configurada.

Permitir únicamente HS256; exigir emisor correcto, sujeto y rol string no vacíos y expiración.
Validar expiración y not-before sin tolerancia de reloj para conservar la política anterior;
los servidores deben sincronizar sus relojes. Exigir exp explícitamente: el validador estándar
de fechas por sí solo permite que falte.

Después de validar el token, cargar la cuenta actual y rechazar usuarios desconocidos,
deshabilitados, bloqueados, con cuenta o credenciales vencidas, o con rol diferente.
Conservar UserDetails como principal y usar sus permisos y nombre para propiedad y límites.
Ubicar RateLimitFilter después de BearerTokenAuthenticationFilter y conservar los estados
401/403 y el filtro externo de correlación.

## Alternativas

RS256/JWKS separa emisor y verificador, pero requiere decidir proveedor de identidad, custodia,
rotación y distribución de claves. Mantener HS256 en este laboratorio de una instancia y
evaluar RS256/JWKS por separado.

## Consecuencias y pendientes

- Los tokens HS256 vigentes con los claims requeridos mantienen compatibilidad estructural.
- Un Bearer inválido ahora falla también en rutas públicas. El esquema Bearer acepta mayúsculas
  y minúsculas según el resolver estándar. No se habilitan tokens por query ni formulario.
- Se conserva la duración predeterminada de 3600 segundos; reducirla sigue pendiente.
- No hay revocación individual, logout de tokens, refresh tokens, denylist ni rotación administrada.
  Deshabilitar una cuenta invalida sus tokens en la siguiente petición, sin sustituir revocación individual.
  No se agregan refresh tokens sin diseñar su ciclo de vida y almacenamiento.
- El rate limiting sigue en memoria por proceso. Faltan control distribuido y pruebas operativas
  de proxies confiables. ZAP con acceso autenticado, cobertura y reportes sigue pendiente.
- Las pruebas Maven raíz cubren autenticación HTTP, manipulación, algoritmos, claims, propiedad,
  401/403/429, estado de cuenta, correlación/MDC y observabilidad. CI ejecuta también PostgreSQL.
  Los módulos independientes `services/` quedan fuera de este cambio y de esa verificación.

## Referencias

- [Documentación oficial de Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Ticket de migración de Grupo Farina](https://app.notion.com/p/400ec61028f081cdbd27f7dee52ead52)
- [Ticket de límites y ciclo de vida](https://app.notion.com/p/400ec61028f0813584bec8a215f733da)
