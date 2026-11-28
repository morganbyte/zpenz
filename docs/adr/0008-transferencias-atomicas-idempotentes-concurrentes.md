# ADR-0008: Transferencias con bloqueo de la cuenta de origen, `Idempotency-Key` guardada en la misma transacción y un token temporal

- **Estado:** Aceptado
- **Fecha:** 2026-11-28

## Contexto

La Fase 3 agrega transferencias: mover dinero de una cuenta a otra. El README promete que una transferencia se registra completa o no se registra, que reintentarla con la misma `Idempotency-Key` no la duplica y que dos operaciones simultáneas sobre la misma cuenta no producen saldos inconsistentes.

Lo que ya existe (ADR-0006 y ADR-0007):

- Un asiento se guarda con todas sus líneas en una sola transacción, y la base rechaza uno descuadrado. Esto ya cubre la atomicidad.
- El saldo no se guarda, se suma. Dos asientos simultáneos no pueden dejar un saldo "descuadrado": no hay ningún campo que se pise.
- El ADR-0006 permite saldos negativos (sobregiros) en los asientos en general.

Por eso la concurrencia solo importa cuando una regla depende del saldo. Esta fase agrega una: **una transferencia no puede dejar en negativo una cuenta de activo**. Si una cuenta de ahorros tiene 100 000 y llegan al mismo tiempo dos transferencias de 70 000, sin control las dos leen 100 000, las dos pasan y la cuenta queda en −40 000.

El criterio de la fase es "endpoint funcionando en la nube", pero el registro y el inicio de sesión llegan en la Fase 5. Un endpoint de escritura abierto en Render permitiría que cualquiera llenara la base gratuita de Neon.

## Decisión

**Transferencia.** Una transferencia es un asiento de dos líneas: crédito a la cuenta de origen y débito a la de destino, por el mismo monto mayor que cero, con fecha y descripción. El origen y el destino deben ser cuentas distintas que existan. Puede ser entre cuentas de cualquier tipo: pagar la tarjeta de crédito desde los ahorros es una transferencia de un activo a un pasivo. Un nuevo caso de uso, `TransferUseCase`, construye el asiento; el motor contable no cambia.

**Fondos suficientes.** Si la cuenta de origen es un activo, su saldo después de la transferencia no puede ser negativo; si lo es, la transferencia se rechaza con `InsufficientFundsException`. La regla aplica solo a transferencias. Los asientos generales y los reversos pueden seguir dejando saldos negativos (ADR-0006), y los pasivos no tienen cupo por ahora.

**Concurrencia: bloqueo pesimista de la cuenta de origen.** En una sola transacción, con aislamiento `READ COMMITTED`:

1. Se bloquea la fila de la cuenta de origen con `SELECT ... FOR NO KEY UPDATE`.
2. Se suma el saldo del origen.
3. Si alcanza, se insertan el asiento y sus líneas.

Una segunda transferencia desde la misma cuenta espera en el paso 1 hasta que la primera termine. En `READ COMMITTED` cada sentencia ve lo confirmado antes de empezar, así que su suma del paso 2 ya incluye la primera transferencia.

Se usa `FOR NO KEY UPDATE` y no `FOR UPDATE` porque insertar una línea que referencia una cuenta toma un `FOR KEY SHARE` sobre ella (la clave foránea). `FOR UPDATE` choca con ese bloqueo y frenaría cualquier asiento que toque la cuenta; `FOR NO KEY UPDATE` no. Como cada transferencia bloquea una sola cuenta, dos transferencias no pueden bloquearse en orden cruzado (no hay *deadlocks* entre ellas).

**Transacciones sin Spring en el dominio.** Un puerto nuevo, `Transactions`, con un método `inTransaction(Supplier<T>)`, deja que el caso de uso marque dónde empieza y termina la transacción sin conocer Spring. `ledger-store` lo implementa con `TransactionTemplate`. El `add` que ya existe se une a la transacción abierta. `AccountRepository` gana `lockById(AccountId)`, que bloquea la cuenta y la devuelve.

**Idempotencia.** `POST /transfers` exige la cabecera `Idempotency-Key`, un texto de 1 a 255 caracteres que genera el cliente (se recomienda un UUID). Una tabla nueva, `idempotency_key`, guarda la clave, el asiento que produjo y cuándo. Dentro de la misma transacción de la transferencia:

1. Se reclama la clave con `INSERT ... ON CONFLICT DO NOTHING`, antes de todo lo demás. El asiento todavía no existe, así que su clave foránea se comprueba al final de la transacción (`DEFERRABLE INITIALLY DEFERRED`).
2. Si la clave ya existía, no se registra nada. Si la petición coincide con el asiento guardado (origen, destino, monto, fecha y descripción), se responde ese mismo asiento. Si no coincide, se rechaza: la clave se reutilizó para otra cosa.

Dos peticiones simultáneas con la misma clave chocan en la llave primaria: la segunda espera a que la primera termine. Si la primera confirmó, la segunda encuentra la clave y devuelve el asiento. Si la primera falló (por ejemplo, sin fondos), su reclamo se deshace y la segunda se procesa desde cero. Un rechazo no queda guardado: reintentar después de una consignación puede pasar.

No hace falta guardar un *hash* de la petición: el asiento guardado ya tiene todos sus datos para compararla. Las claves no expiran por ahora y son globales; en la Fase 5 pasan a ser por usuario.

**Endpoints provisionales.** Para cumplir el criterio de la fase:

| Método y ruta | Qué hace |
|---|---|
| `POST /accounts` | Crea una cuenta. |
| `GET /accounts/{id}/balance` | Devuelve el saldo. |
| `POST /transfers` | Registra una transferencia. Exige `Idempotency-Key`. |

Los errores se responden en formato *Problem Details* (RFC 9457): 400 si falta un dato o la clave; 401 sin token válido; 404 si una cuenta no existe; 422 sin fondos o con una clave reutilizada para otra petición. Una repetición de una transferencia ya registrada responde lo mismo que la original, con la cabecera `Idempotent-Replayed: true`. La Fase 4 fija el contrato de la API con OpenAPI y puede cambiar estas rutas: todavía no hay clientes.

**Token temporal.** Hasta la Fase 5, las rutas de la API (todo lo que no es `/actuator/health` ni `/actuator/info`) exigen la cabecera `Authorization: Bearer <token>`, comparada contra la variable de entorno `API_TOKEN` en tiempo constante (`MessageDigest.isEqual`). Si la variable no está definida, la aplicación no arranca, igual que sin base de datos. El token se configura en el panel de Render, nunca en el repositorio. Lo aplica un filtro en `api-rest`; la Fase 5 lo reemplaza por JWT.

**Pruebas.** Contra PostgreSQL real con Testcontainers:

- Varias transferencias simultáneas desde una cuenta que solo alcanza para algunas: pasan exactamente las que caben y el saldo nunca queda negativo.
- Varias peticiones simultáneas con la misma `Idempotency-Key`: queda un solo asiento y todas reciben el mismo.
- Misma clave con otra petición: 422. Clave de una transferencia rechazada: se puede reintentar.

## Alternativas consideradas

- **Aislamiento `SERIALIZABLE` sin bloqueos explícitos:** PostgreSQL detecta el conflicto, pero lo resuelve abortando una de las transacciones, y la aplicación tiene que reintentarla. Es más difícil de probar y de razonar, y aborta también transacciones que en realidad no chocan.
- **Bloqueo optimista con una columna de versión en la cuenta:** obliga a actualizar la fila de la cuenta en cada asiento (la cuenta deja de ser solo de lectura) y a reintentar ante conflictos. Con una cuenta muy usada, los reintentos se acumulan.
- **Guardar el saldo en la cuenta y descontar con `UPDATE ... WHERE saldo >= monto`:** es la forma clásica y la más rápida, pero contradice el ADR-0006: aparece un campo de saldo que puede dejar de coincidir con los asientos.
- **Bloqueos *advisory* de PostgreSQL:** funcionan, pero el bloqueo queda atado a un número que la aplicación deriva de la cuenta, fuera del modelo de datos. Bloquear la fila de la cuenta es más evidente.
- **Usar la clave de idempotencia como identificador del asiento:** evita la tabla nueva, pero ata un dato del cliente a la llave primaria del libro, y en la Fase 5, con claves por usuario, ya no serviría.
- **Guardar la respuesta HTTP completa por clave:** es lo que hacen algunos proveedores de pagos, pero mezcla HTTP con la persistencia del libro. Basta con guardar el asiento y reconstruir la respuesta.
- **Endpoints abiertos hasta la Fase 5:** más simple, pero cualquiera con la URL podría escribir en la base gratuita.
- **Endpoints solo en local:** no cumple el criterio de la fase ("en la nube").
- **Spring Security para el token:** se agregará en la Fase 5 con JWT. Para un único token compartido basta un filtro de pocas líneas, y se evita configurar Spring Security dos veces.

## Consecuencias

- La regla de fondos vive solo en el caso de uso, no en la base: un asiento general o un reverso pueden dejar un activo en negativo, a propósito. Si se quisiera en la base, se necesitaría un *trigger* que sume el saldo en cada línea, con el mismo bloqueo.
- Las transferencias desde la misma cuenta se registran una tras otra. Para una persona no se nota; si una cuenta recibiera cientos de transferencias por segundo, sería un cuello de botella.
- La transacción ahora la abre el caso de uso, a través del puerto `Transactions`. Una prueba unitaria del caso de uso necesita una implementación falsa que solo ejecute el `Supplier`.
- `idempotency_key` crece sin límite. En la base gratuita de Neon (0,5 GB) tarda mucho en importar, pero habrá que decidir una expiración antes de abrir la aplicación a más personas.
- Crear cuentas no es idempotente: reintentar un `POST /accounts` crea otra cuenta. Se revisará en la Fase 4.
- Hay una variable de entorno nueva, `API_TOKEN`. El CI y `bootTestRun` usan un valor de prueba; Render necesita el real antes del despliegue, o la aplicación no arranca (la misma lección del despliegue de la Fase 2).
- El token compartido no distingue personas: quien lo tenga puede hacer todo. Es aceptable mientras el despliegue solo lo use quien desarrolla el proyecto.
- El `TODO` de `CorsConfig` sobre los métodos permitidos sigue pendiente: estas rutas solo usan `GET` y `POST`.
