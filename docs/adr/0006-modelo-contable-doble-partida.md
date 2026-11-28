# ADR-0006: Modelo contable de doble partida con saldo calculado

- **Estado:** Propuesto
- **Fecha:** 2026-11-28

## Contexto

zpenz promete que el saldo no es un campo que se edita, sino el resultado de asientos de doble partida que nunca se borran (ver los principios del README). La Fase 1 construye ese motor en Java puro, sin Spring ni base de datos, para que las reglas se puedan probar aisladas y no dependan de ningún framework.

Hay que decidir qué tipos de cuenta existen, cómo se registra un movimiento, cómo se corrige un error y cómo se obtiene un saldo. Una persona necesita al menos: lo que tiene (cuentas bancarias, efectivo), lo que debe (tarjetas de crédito, préstamos), lo que entra (salario) y lo que sale (mercado, arriendo).

## Decisión

**Cuentas.** Toda cuenta tiene un identificador, un nombre y uno de cinco tipos. Cada tipo tiene un **lado normal**, que es el lado por el que crece:

| Tipo | Lado normal | Ejemplo para una persona |
|---|---|---|
| Activo (`ASSET`) | Débito | Cuenta de ahorros, efectivo |
| Gasto (`EXPENSE`) | Débito | Mercado, arriendo |
| Pasivo (`LIABILITY`) | Crédito | Tarjeta de crédito, préstamo |
| Patrimonio (`EQUITY`) | Crédito | Saldo inicial |
| Ingreso (`INCOME`) | Crédito | Salario |

La cuenta **no guarda saldo**.

**Asientos.** Un movimiento es un asiento con fecha, descripción y dos o más líneas. Cada línea indica una cuenta, un lado (débito o crédito) y un monto mayor que cero. Un asiento solo se puede crear si:

- tiene al menos una línea de débito y una de crédito, y
- la suma de los débitos es igual a la suma de los créditos.

Un asiento es inmutable: no tiene métodos para cambiar sus líneas, su fecha ni su descripción.

**Reversos.** Un error se corrige con un asiento de reverso: las mismas líneas con los lados invertidos, que referencia al asiento original. El reverso no puede tener una fecha anterior a la del original. Un reverso no se reversa: si el movimiento debía existir, se registra un asiento nuevo.

**Saldo.** El saldo de una cuenta se calcula sumando sus líneas: las del lado normal suman y las del lado contrario restan. Un saldo positivo significa que la cuenta creció en su lado normal (en una tarjeta de crédito, que se debe dinero). Un saldo puede ser negativo, por ejemplo un sobregiro.

**Dinero.** Se aplica el ADR-0004 con dos precisiones: un monto con más de dos decimales se **rechaza** en lugar de redondearse, para que un error de entrada no se convierta en un centavo perdido en silencio; y un monto que no cabe en `NUMERIC(19,2)` también se rechaza.

**Idioma.** El código usa nombres en inglés (`Account`, `JournalEntry`, `Money`), como el resto del proyecto y el scaffold. La documentación, los comentarios y los mensajes de error van en español.

## Alternativas consideradas

- **Campo de saldo en la cuenta, actualizado en cada movimiento:** es más rápido de consultar, pero abre la puerta a que el saldo y los movimientos dejen de coincidir. Si en el futuro hace falta rendimiento, se agrega una proyección del saldo que se reconstruye desde los asientos, sin reemplazarlos.
- **Solo cuentas de activo y gasto:** es más simple, pero no puede representar una tarjeta de crédito ni un ingreso sin trucos de signos, y rompe la doble partida.
- **Montos con signo en lugar de débito y crédito:** cabe en una sola columna, pero el significado de "positivo" cambia según el tipo de cuenta y es fácil equivocarse. Débito y crédito explícitos hacen visible la regla.
- **Permitir editar o borrar un asiento con errores:** se pierde la historia de lo que pasó. Va contra el principio de inmutabilidad.

## Consecuencias

- Las reglas de un asiento se comprueban en su construcción: no puede existir un asiento descuadrado en memoria.
- Calcular un saldo recorre todas las líneas de la cuenta. En la Fase 1 es en memoria; en la Fase 2 será una consulta con `SUM` en PostgreSQL. Si crece demasiado, se evaluará una proyección.
- Que un asiento se reverse **una sola vez** depende de los asientos ya registrados, no del asiento aislado. Esa regla se aplica en la Fase 3, junto con la atomicidad y la concurrencia.
- La Fase 1 no tiene casos de uso: el scaffold registra cada `*UseCase` como bean de Spring y fallaría el arranque sin adaptadores de persistencia. Los casos de uso y sus puertos llegan en la Fase 2.
- Agregar monedas exigiría un ADR nuevo (ver ADR-0004): cada asiento tendría que cuadrar por moneda.
