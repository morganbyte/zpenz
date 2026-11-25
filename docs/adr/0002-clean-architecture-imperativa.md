# ADR-0002: Clean Architecture con el scaffold de Bancolombia en modo imperativo

- **Estado:** Aceptado
- **Fecha:** 2026-11-24

## Contexto

El corazón de zpenz es un motor contable: sus reglas (doble partida, inmutabilidad, reversos) deben poder probarse sin base de datos ni framework, y la infraestructura debe poder cambiar (por ejemplo, RabbitMQ por SQS) sin tocar el dominio.

El [scaffold de Bancolombia](https://bancolombia.github.io/scaffold-clean-architecture/) (plugin de Gradle, versión 5.3.0) genera un proyecto multimódulo que separa dominio, casos de uso, infraestructura y aplicación. Por defecto genera un proyecto **reactivo** (Spring WebFlux), usa Lombok, Java 25 y Pitest.

## Decisión

Usaremos el scaffold con estas opciones:

```
./gradlew ca --name=zpenz --package=io.github.morganbyte.zpenz --type=imperative \
  --java-version=21 --lombok=false --mutation=false --metrics=true
```

- **Imperativo (Spring MVC)** en vez de reactivo: las transferencias dependen de transacciones y bloqueos de fila (`SELECT ... FOR UPDATE`). En el modelo imperativo, una transacción corresponde a un hilo y se lee de arriba abajo; con R2DBC habría que aprender a la vez concurrencia reactiva y concurrencia en base de datos.
- **Java 21** en vez de 25: es la versión LTS elegida para el proyecto e incluye *virtual threads*, que cubren la necesidad de escalar hilos sin pasar a reactivo.
- **Sin Lombok:** genera código oculto en tiempo de compilación. Los `record` de Java cubren los objetos inmutables y todo queda visible.
- **Sin Pitest:** las pruebas de mutación no están en el stack; se prioriza tener pocas herramientas.
- **Con métricas (Micrometer):** forman parte del stack de observabilidad.

## Alternativas consideradas

- **Proyecto Spring Boot de un solo módulo:** más simple al inicio, pero nada impide que el dominio termine dependiendo de JPA o de HTTP.
- **Scaffold reactivo (opción por defecto):** útil para sistemas con mucha E/S concurrente, pero añade complejidad que zpenz no necesita.

## Consecuencias

- Las dependencias apuntan hacia adentro; ArchUnit (incluido por el scaffold) lo verifica en las pruebas.
- Hay más módulos y archivos que en un proyecto plano.
- Gran parte de la configuración de Gradle la aplica el plugin por convención y no se ve en los `build.gradle` de cada módulo.
- Correr `./gradlew ca` otra vez sobre el proyecto reemplazaría archivos raíz; no debe hacerse.
