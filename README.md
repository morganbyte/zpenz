# zpenz

Plataforma de finanzas personales construida por dentro como un **core bancario**.

No es otra app de gastos. Aquí no existe un campo `saldo` que se edita: el saldo se **calcula a partir de asientos contables de doble partida**, nada se borra (se reversa) y dos operaciones simultáneas no se pisan.

> **Estado:** Fase 1 terminada (v0.2.0): el motor contable de doble partida está en el dominio y desplegado en https://zpenz.onrender.com/actuator/health. Siguiente: Fase 2, persistencia con Flyway. Todavía no hay funcionalidad expuesta por la API.

## Principios

1. **Doble partida.** Todo movimiento es un asiento cuyas líneas suman cero: débitos = créditos.
2. **Inmutabilidad.** Los asientos no se editan ni se borran. Un error se corrige con un asiento de reverso.
3. **Atomicidad e idempotencia.** Una transferencia se registra completa o no se registra; reintentarla con la misma `Idempotency-Key` no la duplica.
4. **Concurrencia segura.** Dos operaciones simultáneas sobre la misma cuenta no producen saldos inconsistentes.
5. **Pocas tecnologías, bien aplicadas.** Cada decisión relevante queda escrita en un [ADR](docs/adr/).

## Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 (LTS) |
| Framework | Spring Boot 4 ([ADR-0003](docs/adr/0003-spring-boot-4.md)) |
| Arquitectura | [Scaffold Clean Architecture de Bancolombia](https://bancolombia.github.io/scaffold-clean-architecture/), Gradle multimódulo, modo imperativo ([ADR-0002](docs/adr/0002-clean-architecture-imperativa.md)) |
| Base de datos | PostgreSQL + Flyway (Neon, plan Free) |
| Seguridad | Spring Security + JWT (access y refresh) |
| Mensajería | RabbitMQ (AWS SQS en la demostración final) |
| Pruebas | JUnit 5, Mockito, Testcontainers, JaCoCo, ArchUnit |
| Documentación de API | OpenAPI / Swagger |
| Calidad | GitHub Actions + SonarCloud |
| Contenedores | Docker + Docker Compose |
| Despliegue | Render (plan gratuito) durante las fases 0 a 9; AWS en plan Free para la demostración final ([ADR-0005](docs/adr/0005-despliegue-gratuito-aws-al-final.md)) |
| Observabilidad | Actuator + Micrometer + Prometheus + Grafana |
| Frontend | TypeScript |

## Estructura del proyecto

```
domain/
  model/              Entidades, reglas de negocio y puertos (interfaces). Sin Spring.
  usecase/            Casos de uso: orquestan el dominio a través de los puertos.
infrastructure/
  driven-adapters/    Implementaciones de los puertos (Postgres, RabbitMQ...).
  entry-points/       Por donde entra el mundo (API REST).
  helpers/            Utilidades compartidas por los adaptadores.
applications/
  app-service/        Ensambla los módulos y arranca la app (único main).
deployment/           Dockerfile.
docs/adr/             Registro de decisiones de arquitectura.
```

Las dependencias apuntan **hacia adentro**: `domain` no conoce a Spring, a la base de datos ni a HTTP. ArchUnit verifica esa regla en las pruebas.

## Requisitos

- JDK 21 (recomendado: Temurin vía [SDKMAN](https://sdkman.io/))
- Docker

No hace falta instalar Gradle: el proyecto trae el wrapper (`./gradlew`).

## Cómo compilar y probar

```bash
./gradlew build      # compila, corre pruebas y genera reporte de cobertura
```

El reporte de cobertura de JaCoCo queda en `build/reports/`. Las pruebas de persistencia levantan PostgreSQL con Testcontainers, así que Docker debe estar corriendo.

## Cómo correr en local

```bash
./gradlew :app-service:bootTestRun     # levanta PostgreSQL con Testcontainers y la aplicación
curl localhost:8080/actuator/health    # {"status":"UP", ...}
```

No hace falta instalar PostgreSQL. Los datos se pierden al detener la aplicación.

## Cómo correr con Docker

La aplicación necesita PostgreSQL ([ADR-0007](docs/adr/0007-persistencia-jdbcclient-flyway.md)). Flyway crea las tablas al arrancar.

```bash
docker network create zpenz
docker run -d --name db --network zpenz -e POSTGRES_DB=zpenz -e POSTGRES_USER=zpenz -e POSTGRES_PASSWORD=zpenz postgres:17-alpine
docker build -f deployment/Dockerfile -t zpenz .
docker run --rm --network zpenz -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db:5432/zpenz -e DB_USERNAME=zpenz -e DB_PASSWORD=zpenz zpenz
curl localhost:8080/actuator/health   # {"status":"UP", ...}
```

La imagen compila el proyecto por dentro, así que no hace falta correr `./gradlew build` antes. El puerto se puede cambiar con la variable `PORT`.

| Variable | Para qué |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL. Debe empezar por `jdbc:postgresql://`. |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales de la base. |
| `PORT` | Puerto HTTP (por defecto 8080). |
| `RENDER_GIT_COMMIT` | Commit que muestra `/actuator/info`. Render la define sola. |

## Despliegue

La aplicación corre en el plan gratuito de Render ([ADR-0005](docs/adr/0005-despliegue-gratuito-aws-al-final.md)). El servicio se define en [`render.yaml`](render.yaml) y se despliega solo cuando el CI de `main` pasa.

- **Nunca registres un método de pago en Render.** Sin tarjeta, al agotar los límites gratuitos Render suspende el servicio. Con tarjeta, cobra el excedente.
- El servicio gratuito se duerme tras 15 minutos sin tráfico. La primera petición después de una pausa puede tardar entre 2 y 3 minutos.
- La base es PostgreSQL 17 en el plan gratuito de Neon. `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` se configuran en el panel de Render, nunca en el repositorio. `DB_URL` usa el endpoint **directo** de Neon (sin `-pooler` en el host) con `?sslmode=require`.
- `/actuator/health` incluye la base: si Neon no responde, el servicio aparece caído.
- `/actuator/info` muestra el commit desplegado, para verificar cada despliegue.

## Hoja de ruta

**Regla de avance:** no se empieza una fase sin que la anterior esté **desplegada**, con **pruebas en verde** y este **README actualizado**. Si una fase se alarga, se corre el cronograma; la regla no se salta.

| Fase | Contenido | Hecho cuando | Estado |
|---|---|---|---|
| 0 | Esqueleto: scaffold, Docker, CI, despliegue en Render | `/actuator/health` responde en una URL pública | ✅ Hecho |
| 1 | Motor contable en Java puro | Pruebas del dominio en CI, versión desplegada | ✅ Hecho |
| 2 | Persistencia con Flyway | Migraciones aplicadas en Neon | 🚧 Siguiente |
| 3 | Transferencias atómicas, idempotentes y concurrentes | Endpoint funcionando en la nube | ⏳ |
| 4 | API REST + OpenAPI | Swagger accesible | ⏳ |
| 5 | Registro e inicio de sesión con JWT | Cualquier persona puede crear cuenta | ⏳ |
| 6 | Eventos con outbox + RabbitMQ | Broker en la nube | ⏳ |
| 7 | SonarCloud y cobertura | Quality gate en verde | ⏳ |
| 8 | Observabilidad | Panel de Grafana con métricas reales | ⏳ |
| 9 | Frontend en TypeScript | Desplegado y conectado a la API | ⏳ |
| 10 | Demostración en AWS (plan Free, sin costo) | La aplicación completa corre en AWS, se documenta y se desmonta | ⏳ |

## Decisiones de arquitectura

Ver [docs/adr/](docs/adr/README.md).
