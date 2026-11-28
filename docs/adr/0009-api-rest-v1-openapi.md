# ADR-0009: API REST `v1` documentada con springdoc desde el código, con el contrato guardado en el repositorio

- **Estado:** Propuesto
- **Fecha:** 2026-11-28

## Contexto

La Fase 3 dejó tres rutas provisionales (`POST /accounts`, `GET /accounts/{id}/balance`, `POST /transfers`) y avisó que la Fase 4 fijaría el contrato (ADR-0008). El criterio de esta fase es "Swagger accesible". Además:

- Los casos de uso de registrar un asiento y reversarlo existen desde la Fase 2, pero no tienen ruta. Tampoco hay forma de listar cuentas ni de ver los movimientos de una.
- Reintentar `POST /accounts` crea otra cuenta (ADR-0008).
- `CorsConfig` tiene un `TODO` sobre qué métodos permitir, y acepta cualquier cabecera.
- Las validaciones de los cuerpos JSON están escritas a mano y no aparecerían en una documentación generada.
- El frontend de la Fase 9 será TypeScript: en JavaScript un número JSON es un `double`, y `0.1 + 0.2` no da `0.3`.
- Desde la Fase 3 todavía no hay clientes de la API, salvo la prueba de humo del proyecto: es el último momento barato para cambiar rutas.

## Decisión

**Documentación desde el código.** Usaremos springdoc-openapi 3 (`springdoc-openapi-starter-webmvc-ui`), compilado contra la misma versión de Spring Boot del proyecto. Genera la especificación OpenAPI 3.1 a partir de los controladores, sus anotaciones y las validaciones de los DTOs. Swagger UI queda en `/swagger-ui.html` y la especificación en `/v3/api-docs`. Las anotaciones viven solo en `api-rest`: el dominio no cambia.

**El contrato, en el repositorio.** La especificación generada se guarda en `docs/api/openapi.yaml`. Una prueba arranca la aplicación, pide `/v3/api-docs.yaml` y la compara con ese archivo: si un cambio de código altera la API, la prueba falla hasta que se actualice el archivo. Así todo cambio de contrato aparece como diff en el pull request, aunque nadie haya querido cambiarlo.

**Versión en la ruta.** Todas las rutas quedan bajo `/api/v1`. Un cambio incompatible (quitar o renombrar un campo, cambiar un código de respuesta) exige `/api/v2`; agregar un campo opcional o una ruta nueva, no. Las rutas provisionales de la Fase 3 desaparecen.

**Rutas.**

| Método y ruta | Qué hace |
|---|---|
| `POST /api/v1/accounts` | Crea una cuenta. |
| `GET /api/v1/accounts` | Lista las cuentas con su saldo. |
| `GET /api/v1/accounts/{id}` | Una cuenta con su saldo. |
| `GET /api/v1/accounts/{id}/entries` | Los asientos que tocan la cuenta, del más reciente al más antiguo, paginados. |
| `POST /api/v1/transfers` | Registra una transferencia (ADR-0008). |
| `POST /api/v1/journal-entries` | Registra un asiento de dos o más líneas. |
| `GET /api/v1/journal-entries/{id}` | Un asiento con sus líneas. |
| `POST /api/v1/journal-entries/{id}/reversal` | Reversa un asiento. |

El saldo deja de ser una ruta aparte: viene dentro de la cuenta.

**Idempotencia en todos los `POST`.** Los cuatro `POST` exigen `Idempotency-Key` con las reglas del ADR-0008: la misma petición devuelve el mismo recurso con `Idempotent-Replayed: true`, y otra petición con la misma clave da 422. Una migración nueva (V3) generaliza `idempotency_key` sin editar la V2:

- Agrega `operation` (`ACCOUNT`, `TRANSFER`, `JOURNAL_ENTRY`, `REVERSAL`): una clave usada en una operación no sirve para otra.
- Agrega `account_id`, con clave foránea diferida como la de `journal_entry_id`, y vuelve opcional `journal_entry_id`. Un `CHECK` exige exactamente una de las dos.
- Las filas existentes quedan como `TRANSFER`.

Para reconocer una repetición se compara la petición con el recurso guardado, como en el ADR-0008: no se guarda la respuesta.

**Paginación por cursor.** `GET /accounts/{id}/entries` devuelve hasta `limit` asientos (50 por defecto, 200 como máximo) y un `nextCursor` opaco si hay más. El cursor codifica la posición del último asiento devuelto (fecha, momento de registro e identificador), y la consulta sigue desde ahí con un índice. Como el libro solo agrega, un asiento nuevo no desplaza las páginas ya leídas. La lista de cuentas no se pagina: una persona tiene decenas, no miles.

**Validación con Bean Validation.** Los DTOs de entrada usan anotaciones de Jakarta Validation (`@NotNull`, `@Size`, `@Pattern`), que springdoc publica en el contrato. Un cuerpo inválido responde 400 con todos los campos que fallaron, no solo el primero. El dominio sigue validando por su cuenta: la API es una puerta más, no la única.

**Convenciones del JSON.**

- Los montos son **texto** con dos decimales exactos: `"70000.50"`, nunca `70000.5`. El contrato lo exige con un patrón; un número JSON se rechaza con 400.
- Las fechas son ISO-8601 (`2026-11-28`), los identificadores UUID y los nombres de campo `camelCase`.
- Los errores siguen en *Problem Details* (RFC 9457), con un campo `code` estable (`insufficient-funds`, `idempotency-key-reused`, `entry-already-reversed`, `validation-failed`...). El contrato documenta en cada ruta qué códigos puede responder.

**Swagger UI público, la API no.** `/swagger-ui.html`, sus recursos y `/v3/api-docs` no exigen token: la documentación se puede leer sin credenciales. El contrato declara el esquema `bearer`, así que el botón *Authorize* de Swagger UI permite probar la API con el token. La cabecera `Content-Security-Policy` se relaja solo en las rutas de Swagger UI, en lo mínimo que necesite para funcionar.

**CORS.** Métodos `GET`, `POST` y `OPTIONS`. Cabeceras permitidas: `Authorization`, `Content-Type` e `Idempotency-Key`. Cabeceras expuestas: `Location` e `Idempotent-Replayed`. Los orígenes siguen en configuración. Esto resuelve el `TODO` de `CorsConfig`.

## Alternativas consideradas

- **Contrato primero (escribir el YAML y generar interfaces con openapi-generator):** el contrato manda y el código lo implementa, pero suma un generador al build, código generado que hay que entender y dos fuentes que mantener alineadas. Con un solo equipo y un solo cliente, guardar el contrato generado da la misma revisión de cambios con menos piezas.
- **Spring REST Docs:** documenta a partir de las pruebas, lo que garantiza que lo documentado funciona, pero no da una interfaz para probar la API, y el criterio de la fase es Swagger.
- **Versión en una cabecera o en el *media type* (Spring Framework 7 lo soporta de serie):** más "puro", pero invisible en un `curl` y en los logs. La versión en la ruta se ve en todas partes.
- **Paginación por `offset`:** más simple, pero un asiento registrado entre dos páginas desplaza los resultados (se repite o se salta uno), y cada página cuesta más cuanto más lejos está.
- **Aceptar montos como número JSON:** más cómodo con `curl`, pero un cliente JavaScript puede perder centavos antes de enviar. Rechazarlo hace explícita la regla del ADR-0004.
- **Swagger UI solo en local:** no cumple el criterio de la fase.
- **Swagger UI detrás del token:** protege la forma de la API, pero la forma no es secreta y ver la documentación no da acceso a nada.
- **Idempotencia solo en transferencias:** deja el problema de las cuentas duplicadas y obliga a los clientes a recordar en qué rutas aplica.

## Consecuencias

- springdoc es una dependencia más que tiene que seguir a Spring Boot. Si se queda atrás, una actualización de Boot tiene que esperarla; hoy la versión 3.2.1 está compilada contra la misma Boot 4.2.0 del proyecto.
- Generar la especificación suma memoria y tiempo al arranque. Hay que vigilarlo en el *instance* de 512 MB de Render.
- Cambiar la API obliga a regenerar `docs/api/openapi.yaml`. Es a propósito, pero es un paso más.
- La prueba de humo de la Fase 3 deja de funcionar: usa las rutas provisionales. Se actualiza con las nuevas.
- Rechazar montos numéricos hace los ejemplos con `curl` un poco más largos (`"amount":"100"` en lugar de `"amount":100`).
- `idempotency_key` cambia de forma con la V3. La V2 no se edita (ADR-0007): la V3 la modifica con `ALTER TABLE`.
- La lista de cuentas sin paginar es una deuda consciente. Si una persona llegara a tener miles de cuentas, se pagina con el mismo cursor.
- Hasta la Fase 5 todas las cuentas son de todos: `GET /accounts` lista las de cualquiera que tenga el token. Con JWT, cada persona verá solo las suyas.
