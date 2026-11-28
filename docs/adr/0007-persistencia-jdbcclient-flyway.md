# ADR-0007: Persistir el libro con SQL explícito (`JdbcClient`) y Flyway, con las reglas también en la base

- **Estado:** Aceptado
- **Fecha:** 2026-11-28

## Contexto

La Fase 2 guarda en PostgreSQL (Neon, ADR-0005) el modelo de la Fase 1 (ADR-0006): cuentas, asientos y líneas. El libro solo inserta: un asiento nunca se edita ni se borra, y el saldo es una suma de líneas. En la Fase 3 llegarán transferencias concurrentes, donde importa saber exactamente qué filas se bloquean.

El dominio ya impide crear un asiento descuadrado en memoria, pero la base la pueden tocar otros caminos: una migración mal escrita, un script manual, un error en un adaptador futuro. El instance gratuito de Render tiene 512 MB.

## Decisión

**Acceso a datos.** Usaremos Spring `JdbcClient` con SQL escrito a mano, en un adaptador `ledger-store` generado con el tipo `GENERIC` del scaffold. Las filas se mapean a los objetos del dominio a mano; no hay entidades intermedias.

**Esquema.** Flyway aplica las migraciones al arrancar la aplicación. Tres tablas:

- `account`: identificador, nombre, tipo.
- `journal_entry`: identificador, fecha, descripción y `reversal_of`, que referencia al asiento que reversa.
- `entry_line`: asiento, número de línea, cuenta, lado y monto `NUMERIC(19,2)` (ADR-0004).

**Reglas en la base, además del dominio:**

- `CHECK`: montos mayores que cero, lados y tipos de cuenta válidos, nombres y descripciones no vacíos.
- Un *constraint trigger* diferido al final de la transacción rechaza un asiento cuyos débitos y créditos no sumen lo mismo, o que no tenga ambos lados.
- Triggers que rechazan `UPDATE`, `DELETE` y `TRUNCATE` sobre asientos y líneas.
- `UNIQUE (reversal_of)`: un asiento se reversa una sola vez. Un trigger rechaza reversar un reverso o fecharlo antes del original.

**Saldo.** La base suma débitos y créditos de una cuenta (`SUM` con `FILTER`), y el dominio aplica el lado normal sobre esos totales. Así la regla de signos vive en un solo lugar.

**Atomicidad.** Guardar un asiento inserta el asiento y sus líneas en una sola transacción. Los casos de uso siguen siendo Java puro: no conocen transacciones ni Spring.

**Conexión.** La URL, el usuario y la contraseña llegan por variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). En Neon se usa el endpoint directo, no el *pooler*, porque Flyway usa bloqueos de sesión que el *pooler* en modo transacción no respeta. El pool de conexiones es pequeño (5).

**Pruebas.** Las pruebas de persistencia corren contra PostgreSQL real con Testcontainers, en la misma versión mayor que Neon (17).

## Alternativas consideradas

- **JPA / Hibernate:** el scaffold lo genera y es lo más común en Spring, pero obliga a mantener entidades mutables aparte del dominio inmutable, oculta el SQL que importará en la Fase 3, y su caché y *dirty checking* no aportan en un libro que solo inserta. Consume más memoria al arrancar.
- **Spring Data JDBC:** menos mágico que JPA, pero sigue pidiendo anotaciones o entidades de persistencia y su modelo de agregados no encaja con sumar líneas de muchos asientos.
- **Reglas solo en el dominio:** menos SQL, pero la base quedaría expuesta a cualquier escritura que no pase por el dominio. En un libro contable, que la base misma rechace un asiento descuadrado vale el costo.
- **Hibernate `ddl-auto` en lugar de Flyway:** sin historial de cambios ni control del SQL; descartado.

## Consecuencias

- El mapeo entre filas y objetos se escribe y se prueba a mano.
- PostgreSQL redondea a dos decimales al guardar en `NUMERIC(19,2)`, antes de que corran los `CHECK` y los triggers: una escritura por SQL directo con `10.005` queda en `10.01` sin error. La base no puede rechazarlo; solo el dominio lo hace (ADR-0006). Las pruebas del adaptador documentan este comportamiento.
- Las reglas de doble partida existen dos veces, en Java y en SQL. Las pruebas cubren ambas: las de dominio sin base y las del adaptador contra PostgreSQL insertando filas inválidas directamente.
- La aplicación ya no arranca sin base de datos. El CI levanta un PostgreSQL para verificar la imagen, y Render necesita las variables de Neon antes del despliegue.
- `/actuator/health` incluye el estado de la base: si Neon no responde, Render ve el servicio caído.
- Que un asiento se reverse una sola vez queda garantizado por la base desde ahora, antes de la Fase 3 que prevé el ADR-0006. La concurrencia entre transferencias sigue en la Fase 3.
- Un error en una migración ya aplicada no se edita: se corrige con una migración nueva, igual que un asiento.
