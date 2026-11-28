# Changelog

Todos los cambios relevantes de zpenz se registran aquí.

El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa [Versionado Semántico](https://semver.org/lang/es/). Cada fase de la hoja de ruta cierra con una versión `0.x.0`.

## [Sin publicar]

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
