# ADR-0003: Spring Boot 4 en lugar de Spring Boot 3

- **Estado:** Aceptado
- **Fecha:** 2026-11-24

## Contexto

El stack inicial de zpenz decía Spring Boot 3. Al generar el proyecto, se comprobó que el scaffold 5.3.0 resuelve **Spring Boot 4.2.0** (`./gradlew :app-service:dependencyInsight --dependency org.springframework.boot:spring-boot`).

Además, el soporte open source de Spring Boot 3.5, la última rama 3.x, terminó el **30 de junio de 2026**: ya no recibe parches de seguridad gratuitos.

## Decisión

Usaremos Spring Boot 4, la versión que trae el scaffold.

## Alternativas consideradas

- **Forzar Spring Boot 3.5 sobre el scaffold 5.3.0:** iría contra la herramienta elegida y podría romper la configuración que genera.
- **Usar una versión antigua del scaffold que genere Boot 3:** el proyecto nacería sobre una base sin soporte, inaceptable para un sistema financiero.

## Consecuencias

- Spring Boot 4 trae **Spring Framework 7**, **Spring Security 7** y **Jackson 3**. Jackson 3 cambia el paquete de `com.fasterxml.jackson` a `tools.jackson`.
- Muchos tutoriales y respuestas en internet todavía son de Boot 3. La mayoría de conceptos aplican igual, pero hay que verificar contra la documentación oficial, sobre todo en seguridad (Fase 5).
- Hay que vigilar el fin del soporte open source de la rama 4.x que se use y planear las actualizaciones.
