# Día 34 — Límites de solicitudes

**Fecha:** 23 de octubre de 2026  
**Estado:** implementado y verificado localmente con H2. No es una validación de producción.

## Objetivo

Reducir intentos automatizados contra el inicio de sesión y ráfagas de creación de pagos, sin aplicar límites a lecturas ni mezclar la cuota de distintos clientes.

## Comportamiento

- `POST /auth/login`: máximo configurable de 30 solicitudes por IP en una ventana de un minuto. Cada solicitud consume cuota, también una autenticación correcta. Al excederla, la API responde `429 Too Many Requests` y envía `Retry-After` en segundos.
- `POST /api/payments`: máximo configurable de 100 solicitudes por usuario autenticado en una ventana de un minuto. Si la solicitud aún no tiene usuario autenticado, la clave de reserva usa la IP. El filtro JWT corre antes del límite para identificar al cliente.
- Otras rutas, incluidos los `GET`, no consumen cuota.
- El servicio usa un token bucket: admite una ráfaga inicial hasta la capacidad y repone tokens gradualmente durante la ventana. La cantidad máxima de claves en memoria se limita a 10.000 y se liberan claves inactivas.

Los valores son punto de partida de aprendizaje, no umbrales de negocio ni una recomendación de capacidad para producción. Se pueden modificar en `application.yml` bajo `securepay.rate-limit`.

## Evidencia

`RateLimitServiceTest` comprueba la reposición gradual, el tiempo anunciado hasta el siguiente token y el límite de memoria. `RateLimitFilterIntegrationTest` comprueba el `429` y `Retry-After` para login, la separación por usuario en pagos y que las lecturas sigan funcionando. La suite existente también comprueba que las validaciones de pago conservan sus respuestas cuando no se agota la cuota.

Verificación local: `mvn -B -ntp verify` — resultado anotado al cerrar este día.

## Lo que se aprendió y lo que queda abierto

El límite de login agrupa solicitudes por IP; una red corporativa compartida puede reunir a muchos usuarios legítimos. El límite de pagos se agrupa por identidad autenticada para que un cliente no consuma la cuota de otro. El token bucket permite ráfagas pequeñas y suaviza la reposición, a diferencia de un contador que se reinicia bruscamente al cambiar la ventana.

Los contadores viven solo en memoria y en una instancia: se reinician al reiniciar la aplicación y cada réplica tendría su propio límite. Un despliegue con varias réplicas requiere almacenamiento compartido o límites en un gateway. Detrás de un proxy, `getRemoteAddr()` puede representar al proxy; solo se debe confiar en direcciones reenviadas si el proxy está identificado y elimina valores aportados por el cliente. Aún no se prueban concurrencia de ráfagas, abuso distribuido, configuración real de proxy ni métricas/alertas. Los límites son defensa en profundidad y no reemplazan controles contra abuso.
