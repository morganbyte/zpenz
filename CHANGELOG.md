# Changelog

Todos los cambios relevantes de zpenz se registran aquí.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/). Cada fase de la hoja de ruta cierra con una versión `0.x.0`.

## [Sin publicar]

### Agregado

- ADR-0009: API REST `v1` documentada con springdoc desde el código, con el contrato guardado en el repositorio.
- API v1 bajo `/api/v1`: crear, listar y ver cuentas con su saldo; ver los asientos de una cuenta con paginación por cursor; registrar, ver y reversar asientos; registrar transferencias.
- Swagger UI público en `/swagger-ui.html` y especificación OpenAPI 3.1 en `/v3/api-docs`, con el esquema `bearer` para probar la API desde *Authorize*.
- Contrato guardado en `docs/api/openapi.yaml`: `OpenApiContractTest` falla si el código cambia la API sin actualizarlo.
- `Idempotency-Key` en todos los `POST`. La migración V3 generaliza `idempotency_key`: la clave guarda su operación y puede apuntar a una cuenta o a un asiento.
- Validación con Bean Validation: un cuerpo inválido responde 400 con todos los campos que fallaron (`validation-failed`).
- Códigos de error estables en *Problem Details*, también en los errores de Spring MVC (`not-found`, `invalid-request`...).

### Cambiado

- **Incompatible:** las rutas provisionales de la Fase 3 (`/accounts`, `/accounts/{id}/balance`, `/transfers`) se reemplazan por las de `/api/v1`. El saldo viene dentro de la cuenta.
- **Incompatible:** los montos se envían como texto; un número JSON responde 400.
- El token de la API se exige solo en `/api/`. Actuator, Swagger UI y la especificación son públicos.
- La política de seguridad de contenido se relaja solo en las rutas de Swagger UI (estilos en línea e imágenes `data:`).
- CORS: métodos `GET`, `POST` y `OPTIONS`; cabeceras permitidas y expuestas explícitas. Resuelve el `TODO` de `CorsConfig`.
- Reversar un reverso responde 409 (`entry-is-reversal`) con una excepción propia del dominio.

## [0.4.0] - 2026-11-28

Cierra la Fase 3: las transferencias se registran completas o nada, una sola vez por `Idempotency-Key` y sin sobregirar una cuenta de activo aunque lleguen al mismo tiempo. Verificado en producción.

### Agregado

- ADR-0008 (aceptado): transferencias con bloqueo de la cuenta de origen, `Idempotency-Key` guardada en la misma transacción y un token temporal.
- Transferencias (`TransferUseCase`): un asiento de dos líneas que se registra completo o nada. Una transferencia no puede dejar en negativo una cuenta de activo, aunque lleguen varias al mismo tiempo: la cuenta de origen se bloquea con `FOR NO KEY UPDATE` mientras se revisa su saldo.
- Idempotencia: migración V2 con la tabla `idempotency_key`. Repetir una transferencia con la misma clave devuelve la original sin registrar nada; usar la clave para otra transferencia se rechaza.
- Puerto `Transactions` para que los casos de uso abran una transacción sin conocer Spring.
- Rutas provisionales `POST /accounts`, `GET /accounts/{id}/balance` y `POST /transfers`, con errores en formato *Problem Details* (RFC 9457).
- Pruebas de concurrencia contra PostgreSQL: diez transferencias simultáneas desde una cuenta que alcanza para tres, y ocho reintentos simultáneos con la misma clave.

### Seguridad

- La API exige `Authorization: Bearer` con el token de `API_TOKEN` hasta la Fase 5. `/actuator/health` e `/actuator/info` siguen públicos.

### Cambiado

- La aplicación necesita `API_TOKEN` para arrancar. El CI comprueba que la API responda 401 sin token.

## [0.3.0] - 2026-11-28

Cierra la Fase 2: el libro contable se persiste en PostgreSQL con Flyway, y la migración V1 está aplicada en Neon.

### Agregado

- ADR-0007 (aceptado): persistir el libro con SQL explícito (`JdbcClient`) y Flyway, con las reglas también en la base.
- Persistencia en PostgreSQL (`ledger-store`): migración V1 con cuentas, asientos y líneas en `NUMERIC(19,2)`. La base rechaza asientos descuadrados (al final de la transacción), cambios y borrados de asientos, reversar dos veces un asiento o reversar un reverso.
- Puertos del libro en el dominio y casos de uso: crear cuenta, registrar asiento, reversar asiento y consultar saldo.
- `/actuator/info` muestra el commit desplegado (`RENDER_GIT_COMMIT`).
- Pruebas contra PostgreSQL 17 con Testcontainers, y `./gradlew :app-service:bootTestRun` para correr en local sin instalar PostgreSQL.

### Cambiado

- La aplicación necesita PostgreSQL para arrancar (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), y `/actuator/health` incluye el estado de la base.
- El CI levanta PostgreSQL para verificar la imagen Docker y comprueba que `/actuator/info` reporte el commit.

## [0.2.0] - 2026-11-28

Cierra la Fase 1: el motor contable de doble partida está en el dominio, en Java puro, con sus pruebas en el CI.

### Agregado

- ADR-0006 (aceptado): modelo contable de doble partida con saldo calculado.
- Motor contable en Java puro (`domain/model`): `Money` (COP exacto con dos decimales, rechaza más decimales y montos que no caben en `NUMERIC(19,2)`), cuentas de cinco tipos con su lado normal, asientos inmutables que no pueden existir descuadrados, reversos y saldo calculado desde los asientos.
- Pruebas unitarias del dominio y reglas ArchUnit que fallan el build si el dominio depende de algo fuera del JDK.

### Seguridad

- `/actuator/prometheus` deja de ser público: solo se expone `/actuator/health`. Las métricas se vuelven a abrir, con protección, en la Fase 8.

### Cambiado

- Plugin Clean Architecture de Bancolombia actualizado de 5.3.0 a 5.4.0.

### Corregido

- El build de la imagen Docker en el CI fallaba con 429 (Too Many Requests) de Maven Central al cambiar los archivos de Gradle. Gradle ahora reintenta hasta 6 veces con espera creciente desde 2 s.

## [0.1.0] - 2026-11-27

Cierra la Fase 0: el esqueleto está desplegado y `/actuator/health` responde en https://zpenz.onrender.com.

### Agregado

- Proyecto generado con el scaffold Clean Architecture de Bancolombia 5.3.0 (imperativo, Java 21, Spring Boot 4).
- README con principios, stack, estructura y hoja de ruta.
- ADR-0001 a ADR-0004: uso de ADRs, arquitectura, Spring Boot 4 y representación del dinero.
- ADR-0005: desplegar en planes gratuitos permanentes (Render + Neon) y dejar AWS para una demostración final.
- Entry point REST (Spring MVC + Tomcat) con Actuator: `/actuator/health` (con probes de liveness y readiness) y `/actuator/prometheus`.
- Filtro de cabeceras de seguridad HTTP y configuración de CORS.
- Prueba de punta a punta que levanta la aplicación y verifica que `/actuator/health` responde `UP`.
- CI en GitHub Actions: compila, corre las pruebas y publica los reportes de pruebas y cobertura en cada push a `main` y en cada pull request. También construye la imagen Docker y verifica que el contenedor responda `/actuator/health`.
- Blueprint de Render (`render.yaml`): servicio web gratuito construido desde el `Dockerfile`, con healthcheck en `/actuator/health` y despliegue solo cuando el CI de `main` pasa.
- `Dockerfile` de dos etapas que compila desde el código fuente, con imagen final solo con el JRE. La app toma el puerto de la variable `PORT`.

### Cambiado

- Envío de datos de uso del plugin de Bancolombia desactivado (`analytics=false`).
