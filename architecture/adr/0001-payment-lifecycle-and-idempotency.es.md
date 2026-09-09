# ADR 0001: Ciclo de vida del pago e idempotencia

English version: [Read in English](0001-payment-lifecycle-and-idempotency.md)

## Estado

Aceptada

## Contexto

SecurePay procesa pagos con tarjeta de crédito. Un pago puede tardar en recibir una respuesta final del proveedor de pagos.

El sistema debe evitar cobros duplicados cuando un cliente reintenta la misma solicitud debido a un error de red o a un tiempo de espera agotado.

## Decisión

Cada solicitud de pago debe incluir una `Idempotency-Key`.

SecurePay procesará la primera solicitud con una clave única. Si recibe otra solicitud con la misma clave, no creará otro pago. Devolverá el mismo resultado de la solicitud original.

## Flujo de estados de pago

```text
PENDING
   ├── APPROVED
   ├── REJECTED
   └── UNKNOWN
          ├── APPROVED
          └── REJECTED
```

`REFUNDED` solo puede ocurrir después de `APPROVED`.

## Consecuencias

- Se previenen cobros duplicados.
- Los clientes pueden reintentar de forma segura.
- El sistema debe almacenar las claves de idempotencia y los resultados de pago.
- Los pagos en `UNKNOWN` requieren reconciliación con el proveedor de pagos.
