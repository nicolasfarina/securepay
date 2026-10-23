# Día 27 — Configuración de seguridad

Fecha: 2026-10-23 (America/Buenos_Aires).

Revisión estática de los perfiles Spring, el contenedor, Compose y la política de seguridad. Se sigue la categoría A05:2021 del roadmap; no se inspeccionó un despliegue productivo.

## Qué aprendemos

Una configuración segura tiene que repetirse en cada entorno. Un perfil seguro en `application-prod.yml` no protege una instancia si el despliegue inicia otro perfil. La configuración también incluye endpoints auxiliares, permisos, puertos, usuario del contenedor, secretos y manejo de errores.

## Evidencia observada

- `application.yml` elige `local` como perfil predeterminado. Ese perfil usa H2 y valores de desarrollo predeterminados para la contraseña demo y la clave JWT.
- `application-postgres.yml` exige clave JWT, pero tiene valores predeterminados para URL y usuario de base de datos y para la contraseña demo. Compose los reemplaza por variables obligatorias para su ejecución local.
- `application-prod.yml` exige credenciales por entorno, valida el esquema con Hibernate y deshabilita OpenAPI y Swagger UI. El Dockerfile no activa ese perfil por sí solo; Compose activa `postgres`, no `prod`.
- `SecurityConfig` permite Swagger/OpenAPI cuando esas rutas están habilitadas y deja anónimo el health check. La configuración base oculta mensajes de error y detalles de health.
- Compose limita el puerto publicado a loopback, ejecuta la API como usuario no-root, con filesystem de solo lectura, capacidades descartadas y `no-new-privileges`.

## Evaluación y límites

El perfil `prod` contiene buenas restricciones, pero el repositorio no impone que un despliegue real lo seleccione. Si el artefacto se ejecuta sin perfil, la configuración predeterminada es `local`; esto es un riesgo de despliegue que debe resolverse en el manifiesto de producción y verificarse allí. No hay evidencia aquí de que exista una instancia productiva mal configurada.

Compose es una ayuda local, no una política de producción. El perfil `postgres` mantiene Swagger habilitado según la configuración revisada; el puerto local reduce exposición en este archivo, pero un despliegue con otro mapeo puede cambiarla.

## Práctica pendiente

Antes de producción, declarar explícitamente el perfil de producción, verificar que no se acepten valores demo, comprobar TLS en el borde, revisar cabeceras HTTP y probar que Swagger esté deshabilitado. La prueba debe inspeccionar la instancia iniciada, no solo el archivo YAML.

## Fuentes

- [OWASP A05:2021 — Security Misconfiguration](https://owasp.org/Top10/en/A05_2021-Security_Misconfiguration/)
- Archivos revisados: `application.yml`, `application-local.yml`, `application-postgres.yml`, `application-prod.yml`, `SecurityConfig.java`, `Dockerfile` y `compose.yaml`.
