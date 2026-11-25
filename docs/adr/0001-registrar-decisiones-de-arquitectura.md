# ADR-0001: Registrar decisiones de arquitectura con ADRs

- **Estado:** Aceptado
- **Fecha:** 2026-11-24

## Contexto

zpenz tiene como regla de oro "pocas tecnologías bien aplicadas, con pruebas y decisiones explicadas". El código muestra *qué* se hizo, pero no *por qué* ni qué alternativas se descartaron. Esa información se pierde con el tiempo si solo vive en conversaciones.

## Decisión

Registraremos cada decisión de arquitectura relevante como un ADR en `docs/adr/`, con el formato de Michael Nygard (contexto, decisión, alternativas, consecuencias). Los ADR viven en el repositorio, junto al código, y se revisan en los mismos commits que el cambio que justifican.

## Alternativas consideradas

- **Wiki externa (Notion, Confluence):** se desincroniza del código y no queda en el historial de git.
- **Solo comentarios en el código:** sirven para el *cómo* de una línea, no para decisiones que cruzan módulos.

## Consecuencias

- Cada decisión importante cuesta escribir un archivo corto.
- Cualquier persona puede entender la historia del diseño leyendo `docs/adr/` en orden.
- Los ADR aceptados son inmutables; cambiar de opinión exige un ADR nuevo que reemplace al anterior.
