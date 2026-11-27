# ADR-0005: Desplegar en planes gratuitos permanentes y dejar AWS para una demostración final

- **Estado:** Aceptado
- **Fecha:** 2026-11-27

## Contexto

zpenz es un proyecto para practicar y aprender, y no generará ingresos. La restricción es **no pagar nada, ni por error**. La hoja de ruta exige que cada fase quede **desplegada** antes de empezar la siguiente, así que hace falta un lugar donde la aplicación esté publicada durante todo el proyecto, que serán varios meses.

El plan inicial era AWS (RDS + App Runner). Al revisarlo en noviembre de 2026:

- **App Runner dejó de aceptar clientes nuevos el 30 de abril de 2026.** AWS recomienda ECS Express Mode como reemplazo.
- Las cuentas de AWS creadas desde el 15 de julio de 2025 eligen un **plan Free** o un **plan Paid**:
  - El plan Free no cobra, pero tiene un límite: unos 6 meses o 100 USD en créditos (más hasta 100 USD por actividades), lo que pase primero. Al terminar, la cuenta se cierra y AWS borra sus datos a los 90 días.
  - Algunas acciones, como unirse a AWS Organizations, pasan la cuenta al plan Paid automáticamente.
  - Ni RDS ni el cómputo para contenedores tienen una capa gratuita permanente.
- Las diez fases no caben en seis meses, y un plan Paid podría generar cobros.

Una aplicación Spring Boot pequeña cabe en unos 512 MB de RAM si se limita el heap. El `Dockerfile` ya usa `-XX:MaxRAMPercentage=70`.

## Decisión

- **Fases 0 a 9:** se despliegan solo en servicios con **plan gratuito permanente** que, al llegar a su límite, **suspendan el servicio en vez de cobrar**:
  - **Aplicación:** Render, *web service* gratuito, construido desde `deployment/Dockerfile` con despliegue automático desde GitHub. La documentación oficial confirma las compilaciones Docker y el despliegue automático para el plan Hobby, pero no dice explícitamente que valgan para las instancias gratuitas. Se comprobará al crear el servicio.
  - **Base de datos:** Neon, plan Free de PostgreSQL. No se usa la base de datos gratuita de Render porque expira a los 30 días.
- **Fase 10, demostración en AWS:** al final, con la aplicación terminada, se despliega en una cuenta de AWS en **plan Free** usando los créditos. Se documenta y luego se desmonta. El servicio de cómputo se elegirá en su propio ADR, con ECS Express Mode como candidato.
- Las fases que necesiten otros servicios (mensajería, observabilidad) los eligen con la misma regla y lo dejan escrito en un ADR.
- **Regla de cuentas:** nunca se registra una tarjeta en una cuenta si el plan gratuito funciona sin ella. Si un proveedor la exige, se confirma antes, en su documentación oficial, que su plan gratuito suspende el servicio y no cobra. En AWS se crea una alerta de AWS Budgets desde el primer día.
  - **Render:** nunca se registra un método de pago. Sin tarjeta, al agotar el ancho de banda o los minutos de build, Render suspende los servicios o desactiva los builds hasta el mes siguiente. Con tarjeta, cobra el excedente.
  - **Neon:** el plan Free no pide tarjeta y no cobra excedentes. Al llegar al límite, suspende el cómputo o rechaza escrituras, sin borrar datos.

## Alternativas consideradas

- **AWS desde el inicio en plan Free:** se aprende la nube que se usará en el trabajo, pero el plazo de unos 6 meses no alcanza para todas las fases. Al vencer, la cuenta y sus datos desaparecen.
- **AWS en plan Paid con alertas de presupuesto:** las alertas avisan, pero no detienen los cobros. Va contra la restricción.
- **Oracle Cloud Always Free:** da una VM ARM generosa y permanente, pero exige tarjeta. En 2026 sus límites cambiaron y sus reglas sobre recuperar máquinas inactivas no están claras. Además, obligaría a administrar un servidor: sistema operativo, parches y TLS. Eso son más tecnologías, y va contra el principio 5.
- **Fly.io y Google Cloud Run:** Fly.io ya no tiene plan gratuito. Cloud Run exige una cuenta de facturación, lo que deja abierta la puerta a cobros.

## Consecuencias

- El costo es cero y no hay plazo para terminar el proyecto.
- **Arranque en frío:** el servicio gratuito de Render se duerme tras 15 minutos sin tráfico y tarda alrededor de un minuto en despertar. Neon suspende el cómputo tras 5 minutos de inactividad, pero vuelve en unos cientos de milisegundos. La lentitud después de una pausa viene casi toda de Render. Es aceptable para un proyecto de práctica, pero las pruebas de carga (Fase 3) deben correr en local con Testcontainers.
- **Recursos de Render:** 512 MB de RAM y **0,1 de CPU**. Con tan poca CPU, el arranque de Spring Boot puede tardar más de un minuto. Hay que vigilar el consumo de la JVM y el tiempo de arranque a medida que se agreguen dependencias.
- **Límites de Render:** 750 horas de instancia, 5 GB de ancho de banda saliente y 500 minutos de build al mes. Render puede reiniciar el servicio en cualquier momento, y el disco se borra en cada reinicio, así que nada se guarda en el sistema de archivos. Bloquea los puertos SMTP 25, 465 y 587.
- **Tráfico hacia la base de datos:** Render puede suspender un servicio gratuito que genere un volumen de tráfico saliente inusualmente alto, y pone como ejemplo el acceso a una base de datos externa. Neon es externa. Con el tráfico de un proyecto de práctica no se espera, pero si pasa, este ADR debe revisarse.
- **Límites de Neon:** 1 GB de almacenamiento, 100 CU-hora y 5 GB de tráfico al mes por proyecto.
- **Sin backups programados en Neon Free:** solo se puede restaurar a un punto de las últimas 6 horas y guardar 1 snapshot manual. Los datos deben poder recrearse con las migraciones de Flyway y datos de prueba.
- La aplicación debe leer la URL de la base de datos y los secretos desde **variables de entorno**, sin nada atado a un proveedor. Así, cambiar a AWS en la Fase 10 es un despliegue nuevo y no una reescritura.
- Los planes gratuitos cambian con frecuencia. Si alguno empieza a exigir pago, se escribe un ADR que reemplace a este.
- La experiencia en AWS se reduce a la Fase 10.
