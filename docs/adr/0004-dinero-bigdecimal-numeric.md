# ADR-0004: Representar el dinero con `BigDecimal` y `NUMERIC(19,2)`, solo COP

- **Estado:** Aceptado
- **Fecha:** 2026-11-24

## Contexto

Los tipos de punto flotante (`double`, `float`) no pueden representar con exactitud la mayoría de fracciones decimales: `0.1 + 0.2` da `0.30000000000000004`. En un libro contable, un error de un centavo rompe la regla de que débitos y créditos sumen lo mismo.

zpenz manejará ingresos y gastos de cualquier persona registrada, en una sola moneda.

## Decisión

- En Java, todo monto es `BigDecimal` con escala 2.
- En PostgreSQL, toda columna de monto es `NUMERIC(19,2)`: 17 dígitos enteros y 2 decimales, exactos.
- La única moneda soportada es **COP**. El modelo no incluye moneda por línea todavía.
- Reglas de uso:
  - Construir desde texto o enteros (`new BigDecimal("1500.50")`), **nunca** desde un `double`.
  - Comparar con `compareTo`, no con `equals` (`2.0` y `2.00` no son `equals`).
  - Normalizar con `setScale(2, RoundingMode.HALF_EVEN)` (redondeo bancario).

## Alternativas consideradas

- **`double`:** inexacto; descartado.
- **`long` con centavos:** exacto y rápido, pero obliga a convertir en cada borde (API, base de datos) y es fácil confundir pesos con centavos.
- **Multimoneda desde el inicio:** exige tasas de cambio, cuentas por moneda y reglas de conversión; complejidad que hoy no aporta.

## Consecuencias

- La aritmética con `BigDecimal` es más verbosa que con primitivos.
- Las reglas de construcción y comparación deben respetarse en todo el código y vigilarse en revisión y pruebas.
- Si en el futuro se agregan monedas, se necesitará un ADR nuevo y una migración.
