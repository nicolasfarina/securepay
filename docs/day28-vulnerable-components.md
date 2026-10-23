# Día 28 — Componentes vulnerables y desactualizados

Fecha: 2026-10-23 (America/Buenos_Aires).

Revisión estática de inventario, actualización y escaneo. No se actualizaron dependencias ni se generó un inventario de vulnerabilidades fuera de los controles de CI ya existentes.

## Qué aprendemos

Un número de versión no dice por sí mismo si un componente es seguro. Hay que saber qué componentes directos y transitivos están presentes, qué versiones tienen, si reciben mantenimiento, qué vulnerabilidades aplican y si hay una actualización compatible. Un SBOM ayuda a responder «qué tenemos»; un escáner compara ese inventario con avisos conocidos; el triage decide qué hacer.

## Evidencia observada

- `pom.xml` hereda versiones administradas por Spring Boot y fija algunas versiones sensibles de Tomcat, Jackson y plugins.
- Dependabot está configurado semanalmente para los dos servicios Maven adicionales, Docker/Compose y GitHub Actions.
- CI ejecuta Trivy sobre manifiestos y sobre la imagen final; bloquea vulnerabilidades High/Critical corregibles y genera un SBOM de imagen. Maven genera un SBOM de dependencias Java.
- La ejecución local de `mvn -B -ntp verify` del Día 26 produjo un SBOM CycloneDX 1.6 con 113 componentes. Ese número es inventario, no un conteo de vulnerabilidades ni una conclusión de seguridad.
- En el commit `c3e605e` de PR #33, CI #114 y Secret Scan #102 completaron con éxito. Según la política del workflow, no hubo hallazgo que activara el bloqueo. Esto no equivale a cero vulnerabilidades: el gate excluye hallazgos sin corrección y severidades menores.

## Evaluación y límites

Hay inventario automatizado, escaneo y actualización periódica configurados. El resultado de un escaneo depende de la fecha, la base de avisos, el alcance del escáner y la política de severidad. La documentación no afirma que todas las dependencias estén libres de vulnerabilidades ni que los avisos de Dependabot hayan sido revisados.

## Práctica pendiente

Para aprender el ciclo completo: abrir el artefacto SBOM de una ejecución, localizar una dependencia directa y una transitiva, comparar el resultado de Trivy con el SBOM, revisar un aviso concreto y justificar «actualizar», «mitigar» o «aceptar temporalmente». No actualizar automáticamente sin ejecutar las pruebas.

## Fuentes

- [OWASP A06:2021 — Vulnerable and Outdated Components](https://owasp.org/Top10/en/A06_2021-Vulnerable_and_Outdated_Components/)
- [Evidencia del Día 17 — escaneo de dependencias](day17-dependency-scanning.md)
- [Evidencia del Día 20 — SBOM](day20-sbom.md)
- [PR #33 y verificaciones](https://github.com/nicolasfarina/securepay/pull/33)
