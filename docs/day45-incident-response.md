# Día 45 — Tablero operativo y respuesta a incidentes

## Objetivo

Dar al operador un lugar y un orden claro para entender alertas, verificar el impacto y actuar sin empeorar un incidente de pagos. Este documento es el diseño del tablero y runbook para el laboratorio; no declara configurada una guardia ni un servicio productivo.

## Tablero operativo

El tablero debe mostrar, en este orden:

1. **Disponibilidad de señales:** scrape de SecurePay (`up`), última muestra y estado de Prometheus/Alertmanager. Un scrape fallido también puede ser un problema de red o autenticación.
2. **Salud de la API:** readiness y liveness separadas. Readiness no saludable significa que la instancia no debería recibir tráfico; liveness no saludable puede justificar reinicio según el orquestador.
3. **Errores de pagos:** tasa y cantidad de respuestas 5xx para `/api/payments`, con el número total de solicitudes visible para dar contexto al porcentaje.
4. **Latencia de pagos:** distribución y proporción de respuestas exitosas debajo de 500 ms, junto con el volumen de muestra.
5. **Persistencia y dependencias:** disponibilidad de PostgreSQL y fallos/latencia de dependencias cuando se integren. No inferir el estado del proveedor de pagos a partir de la salud de PostgreSQL.
6. **Estado de pagos no resueltos:** conteo y antigüedad de `UNKNOWN` solo cuando SecurePay tenga proveedor, referencias y flujo auditable de conciliación. No añadir IDs de pago, clientes, comercios ni trazas como etiquetas de métricas.

Las reglas actuales de Día 42 son umbrales de laboratorio: scrape fallido por 2 minutos, más de 5% de 5xx y menos de 95% de 2xx bajo 500 ms, con al menos 20 solicitudes en 5 minutos y condición sostenida por 2 minutos. Con poco tráfico las reglas de porcentaje no alertan. Prometheus usa almacenamiento efímero; no hay SLO medido en ventana durable ni notificación externa.

## Clasificación y primeras acciones

| Severidad de laboratorio | Señal | Acción inicial |
|---|---|---|
| Warning | `SecurePayScrapeFailed` | Comprobar API, red, target y credencial de scrape; comparar readiness y liveness antes de declarar caída. |
| Warning | `SecurePayHighServerErrorRate` | Verificar ventana y cantidad de solicitudes; revisar logs estructurados por correlation ID, readiness y PostgreSQL; preservar evidencia y no reiniciar a ciegas. |
| Warning | `SecurePaySlowPayments` | Confirmar volumen y ventana; revisar latencia de API y base de datos, saturación y cambios recientes. |
| Pendiente de instrumentación | Pagos `UNKNOWN` envejecidos | No reintentar un cobro ni crear reemplazo. Localizar pago por ID en sistema autorizado y seguir el [runbook de conciliación](../security/runbooks/unknown-payment-reconciliation.es.md); mantener `UNKNOWN` hasta evidencia confiable del proveedor. |

`Warning` describe etiquetas del laboratorio, no un compromiso de tiempo de respuesta. No existe paging ni canal de guardia configurado.

## Runbook de incidente

1. **Confirmar la señal:** abrir alerta, expresión, ventana, hora y muestra; distinguir caída de scrape de errores observados por clientes.
2. **Determinar alcance:** revisar disponibilidad, tasa de errores, latencia, volumen y si afecta todas las solicitudes o una dependencia. Registrar hora de inicio y último cambio conocido.
3. **Proteger pagos:** no repetir escrituras ni modificar estados manualmente. Mantener resultados ambiguos como `UNKNOWN`; usar idempotency key y reconciliación autorizada cuando el flujo exista.
4. **Investigar con datos mínimos:** correlacionar logs usando correlation/trace ID; no copiar JWTs, secretos, datos de tarjeta ni datos personales en tickets o chats.
5. **Mitigar con cambio reversible:** seguir el procedimiento aprobado de rollback o aislamiento del componente afectado. Hoy no hay entorno de despliegue ni procedimiento productivo aprobado en este repo.
6. **Verificar recuperación:** confirmar readiness, scrape, errores, latencia y persistencia; para `UNKNOWN`, exigir estado verificado por el proveedor y evento auditable.
7. **Cerrar y aprender:** registrar impacto, cronología, causa confirmada o hipótesis, acciones y follow-ups. No declarar resuelto por el solo hecho de que la alerta se haya despejado.

## Límites conocidos y tareas para completar

- El receptor de Alertmanager es local, efímero y no envía correo. El destino decidido es el administrador por email, pero falta dirección, relay/API, dominio remitente, secretos y prueba de entrega.
- Falta responsable de guardia, cobertura, escalamiento, suplente y expectativas de respuesta.
- Aprobar o cambiar los SLO propuestos en Día 41; decidir tratamiento de bajo tráfico, endpoints y retención.
- Prometheus de laboratorio tiene retención en memoria. Antes de confiar en tendencias, definir backend duradero, retención, acceso y backups.
- Faltan proveedor de pagos, llamadas de salida, webhooks verificados, eventos de auditoría y conciliación ejecutable; por eso hoy no existe una métrica real de pagos `UNKNOWN`.
- Los umbrales de Día 42 requieren validación con tráfico representativo antes de usarse operacionalmente.

## Verificación y estado

Este primer entregable es documentación derivada de las alertas y runbooks existentes. No modifica el runtime, no publica dashboards en una instancia y no configura correo o guardia. Validar formato y enlaces en revisión del PR; el tablero visual y la integración de notificaciones siguen como trabajo posterior cuando se decidan destino y backend.
