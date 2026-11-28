# Registro de decisiones de arquitectura (ADR)

Un ADR documenta **una** decisión importante: el contexto en que se tomó, qué se decidió y qué consecuencias trae. Se escriben para quien llegue después (incluido el yo del futuro) y pregunte "¿por qué está hecho así?".

Reglas:

- Un ADR aceptado **no se edita**. Si la decisión cambia, se escribe uno nuevo que lo reemplaza y el viejo pasa a estado `Reemplazado por ADR-XXXX`. Igual que los asientos contables de zpenz: no se borra, se reversa.
- Se numeran en orden y nunca se reutiliza un número.
- Para crear uno nuevo, copia [template.md](template.md).

| # | Decisión | Estado |
|---|---|---|
| [0001](0001-registrar-decisiones-de-arquitectura.md) | Registrar decisiones de arquitectura con ADRs | Aceptado |
| [0002](0002-clean-architecture-imperativa.md) | Clean Architecture con el scaffold de Bancolombia en modo imperativo | Aceptado |
| [0003](0003-spring-boot-4.md) | Spring Boot 4 en lugar de Spring Boot 3 | Aceptado |
| [0004](0004-dinero-bigdecimal-numeric.md) | Representar el dinero con `BigDecimal` y `NUMERIC(19,2)`, solo COP | Aceptado |
| [0005](0005-despliegue-gratuito-aws-al-final.md) | Desplegar en planes gratuitos permanentes y dejar AWS para una demostración final | Aceptado |
| [0006](0006-modelo-contable-doble-partida.md) | Modelo contable de doble partida con saldo calculado | Aceptado |
