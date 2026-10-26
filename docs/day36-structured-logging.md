# Día 36 — Structured Logging y Correlation ID

Fecha: 2026-10-26 (America/Buenos_Aires).

## Implementación y contrato

Base inspeccionada: `16f5f8b` de `nicolasfarina/securepay`, con Día 35 integrado.
Implementado en `codex/day36-correlation-id` y fusionado en `main` mediante el [PR #40](https://github.com/nicolasfarina/securepay/pull/40).

- `CorrelationIdFilter` se registra una sola vez como filtro servlet, antes de Spring Security,
  para cubrir también respuestas tempranas de autenticación y rate limiting.
- Acepta exactamente un `X-Correlation-ID` de 1 a 64 caracteres ASCII: letras, números,
  guion y guion bajo. Entradas ausentes, vacías, duplicadas, demasiado largas o inválidas
  se reemplazan por un UUID. No se registran las entradas rechazadas.
- El ID aparece en la respuesta y en la clave MDC `correlationId` durante el dispatch.
  Un atributo interno del request conserva el ID durante redispatches ASYNC y ERROR.
- `finally` elimina la clave o restaura su valor anterior, sin borrar claves ajenas.
  Las excepciones se propagan sin reemplazarlas ni registrar sus mensajes.
- Spring Boot produce JSON Logstash. El evento nuevo `HTTP dispatch completed` contiene
  el ID y `httpStatus`; no incluye cuerpos, URL, query, Authorization, contraseñas ni JWT.
  Un dispatch que lanza una excepción no genera este evento de finalización.
  El ERROR dispatch posterior puede emitir su propio evento; no es un contador de requests.
- Un ID aceptado sigue siendo dato controlado por el cliente: sirve para correlacionar,
  no para autenticar, autorizar, identificar al actor ni demostrar unicidad global.
- `MdcTaskDecorator` se registra como bean `TaskDecorator`; Spring Boot lo aplica a su
  executor auto-configurado. Captura el mapa al enviar cada tarea, instala esa copia en el
  worker y restaura en `finally` el mapa que el worker tenía antes. La prueba HTTP verifica
  que un `Callable` MVC lee el Correlation ID desde el hilo asíncrono y que
  `CompletableFuture.supplyAsync` lo conserva al recibir explícitamente ese executor.

## Pruebas

`CorrelationIdFilterTest` cubre encabezados válidos, límites, ausencia, CR/LF, tabuladores,
caracteres fuera de la lista, duplicados, limpieza y restauración del MDC, excepciones
Servlet/IO/runtime, redispatch ASYNC/ERROR, ERROR anidado, peticiones concurrentes y
reutilización de un worker. También demuestra que un executor independiente no hereda el MDC.

`CorrelationIdHttpTest` usa el contexto real de la aplicación con MockMvc y Spring Security:
controlador de prueba, health público, 401, 403 por rol, 404, 409, 429, excepción no manejada,
dispatch asíncrono, captura de JSON con MDC y ausencia de la entrada CR/LF en los logs.
Los controladores de prueba existen únicamente en `src/test`.

`MdcTaskDecoratorTest` verifica el mapa completo del contexto enviado, un worker con contexto
previo, excepción durante una tarea y una tarea enviada sin MDC. El `finally` restaura
incluso tras el fallo y evita que un contexto residual contamine el siguiente trabajo.

Ejecutar desde la raíz:

```powershell
mvn -B '-Dtest=CorrelationIdFilterTest' test
mvn -B '-Dtest=CorrelationIdHttpTest' test
mvn -B verify
```

La suite raíz usa H2 por defecto. Las pruebas PostgreSQL son opt-in; habilitarlas con
`scripts/test-integration.ps1` levanta una base temporal y prueba PostgreSQL real. MockMvc
ayuda a aislar respuestas y seguridad; `ApiEndToEndTest` añade una comprobación por HTTP
contra Tomcat en un puerto temporal.

### Evidencia ejecutada

Verificación final: `scripts/test-integration.ps1`, ejecutado el 2026-10-26 con Java 25,
Maven 3.9.16, Tomcat 11.0.25 y PostgreSQL temporal 18.6.
Resultado: `BUILD SUCCESS`; 107 casos contabilizados, todos pasaron, sin fallos, errores
ni omisiones. SpotBugs: 0 incidencias y 0 errores.
Los 33 tests nuevos de filtro, HTTP y propagación MDC pasaron.
El script tolera los avisos de Mockito/JVM enviados por Maven a stderr y conserva el código
de salida como criterio de éxito. La prueba eliminó el contenedor temporal al terminar.
Los módulos independientes bajo `services/` no están incluidos en el POM raíz ni en estos resultados.

## Concurrencia y asincronía: alcance comprobado

MDC es local al hilo. Dos requests simultáneos no deben compartir su contexto y un hilo
reutilizado no debe conservar el ID anterior. Los tests verifican ambas propiedades.

Reejecutar el filtro en ASYNC restaura el ID durante el dispatch servlet de continuación;
el `TaskDecorator` del executor de Spring propaga el MDC al `Callable` MVC y al
`CompletableFuture` cuando recibe explícitamente `applicationTaskExecutor`. El test HTTP
comprueba ambos caminos. La propagación no se extiende a `@Async` con otro executor,
`CompletableFuture` usando el common pool, `DeferredResult` producido desde otro hilo ni a
un executor arbitrario que no tenga instalado el decorador.

No hay endpoints asíncronos de producción en esta base. Antes de introducirlos, implementar
propagación explícita en el executor elegido o un interceptor MVC para `Callable`: capturar
el contexto al enviar el trabajo, instalarlo en el worker y restaurar su contexto anterior
en `finally`. Para `DeferredResult`, cubrir el executor productor por separado. Agregar
tests con workers reutilizados, errores, cancelación y timeout en ese punto.

El JSON no elimina por sí solo datos sensibles que otros loggers del framework ya emitan.
Este cambio no completa una auditoría de seguridad, retención, acceso a logs ni alertas.

## Fuentes

- [Spring Boot: JSON estructurado y MDC](https://docs.spring.io/spring-boot/reference/features/logging.html)
- [Spring Framework: OncePerRequestFilter y tipos de dispatch](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/filter/OncePerRequestFilter.html)
