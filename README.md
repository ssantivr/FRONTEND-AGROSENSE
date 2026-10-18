# AgroSense · Frontend

Interfaz web de AgroSense, generada en el servidor con Java 21, Spring Boot, Spring MVC y Thymeleaf.
Los scripts del navegador están en TypeScript estricto (`src/main/ts`); no usa frameworks de JavaScript.

## Ejecutar en modo demostración

No necesita PostgreSQL: usa H2 en memoria y crea las tablas y los datos de ejemplo a partir de
`../database/schema.sql` y `../database/seed_demo.sql`, así que hay que ejecutarlo desde esta carpeta.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo      # Linux / macOS
mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"  # Windows
```

Abre http://localhost:8080 e ingresa con `demo@agrosense.co` / `agrosense`.
La contraseña de la demo se puede cambiar con la variable `DEMO_PASSWORD`.

## Ejecutar contra PostgreSQL

| Variable                | Descripción                                             |
|-------------------------|---------------------------------------------------------|
| `DATABASE_URL`          | URL JDBC, por ejemplo `jdbc:postgresql://host:5432/agrosense` |
| `DATABASE_USERNAME`     | Usuario de la base de datos                             |
| `DATABASE_PASSWORD`     | Contraseña                                              |
| `PORT`                  | Puerto HTTP (por defecto 8080)                          |
| `SESSION_COOKIE_SECURE` | `false` solo si se sirve sin HTTPS (por defecto `true`) |
| `BACKEND_URL`           | Dirección de la API del backend, por ejemplo `https://backend-agrosense-u5mz.onrender.com`. Opcional |
| `BACKEND_JWT_SECRET`    | El mismo valor que `JWT_SECRET` del backend. Opcional   |
| `BACKEND_SIGN_IN`       | `false` para que el inicio de sesión no pase por el backend (por defecto `true`) |
| `EVENTS_PUBLIC_URL`     | Dirección pública del manejador de eventos (`event-service/`), tal como la alcanza el navegador, por ejemplo `http://localhost:8100`. Opcional: sin ella no hay datos en vivo |

Con `BACKEND_URL` y `BACKEND_JWT_SECRET` definidas, iniciar sesión, crear cuentas, atender alertas, registrar sensores y riegos y la
gráfica de lecturas pasan por la API del backend; sin ellas, o mientras el backend no responda, todo se
hace contra la base de datos.

El asistente de IA (la ventana de chat de las páginas con sesión, por texto y por voz) solo funciona con
el backend: sus scripts están en `src/main/ts/ai`, llaman a `/api/ui/ai/chat` y `/api/ui/ai/voice`, y el
servidor reenvía la pregunta al backend, que la responde (o la pasa al servicio de IA) con las llaves que
solo él conoce. Para el usuario no cambia nada.

**Lecturas y alertas en vivo.** Con `EVENTS_PUBLIC_URL`, `src/main/ts/events/live.ts` pide a
`/api/ui/events/token` un token de 5 minutos (lo firma el backend), abre el canal del manejador de eventos
(`EventSource`) y, a medida que llegan eventos de las Raspberry, actualiza la última lectura de cada sensor
(tarjetas y tabla de Sensores, marcadas con `data-sensor-code`) y avisa de cada alerta nueva, sumándola a
los contadores del menú y de la barra superior. Renueva el token antes de que venza y, si se corta, vuelve
a conectar con una espera creciente (1 s, 2 s, 4 s… hasta 1 min). «En vivo», junto al reloj, indica que el
canal está abierto. Sin la variable, o si el manejador de eventos o el backend fallan, las páginas muestran
lo que cargaron, como siempre. La CSP solo añade a `connect-src` el origen de esa dirección, y el navegador
nunca recibe una llave de servicio.

Las tablas se crean con `../database/schema.sql` (ver [`database/README.md`](../database/README.md));
con `SQL_INIT_MODE=always` la aplicación lo ejecuta al arrancar.

Al iniciar sesión por el backend todas las peticiones le llegan desde la dirección de este servidor:
configura en el backend `LOGIN_MAX_ATTEMPTS_PER_ADDRESS=0` para que los fallos de un visitante no bloqueen
a los demás (el límite por cuenta sigue activo).

## Scripts (TypeScript)

El código fuente está en `src/main/ts` y `tsc` lo compila a `src/main/resources/static/js`, que se guarda
en el repositorio: Maven y el despliegue no necesitan Node. Solo hace falta para cambiar los scripts:

```bash
npm install
npm run build       # o: npm run watch
npm run typecheck
```

No edites `static/js` a mano: la siguiente compilación lo sobrescribe.

Las transiciones de vista entre páginas (`@view-transition`) están desactivadas: Chromium abortaba la
transición en casi cada navegación y lo anotaba en la consola como error («Transition was aborted because of
invalid state»). Quitarla solo al ir al inicio no lo evitaba. Una prueba de `WebFlowTests` impide que la regla
vuelva a `styles.css`.

## Pruebas

```bash
./mvnw clean verify
```

### En cada pull request

`.github/workflows/neon-pull-request.yml` crea una copia de la base de datos en Neon para el pull
request (`preview/pr-<número>`), ejecuta todas las pruebas contra ella, incluidas las de PostgreSQL, y la
borra cuando el pull request se cierra. Necesita el secreto `NEON_API_KEY` y la variable
`NEON_PROJECT_ID`, que crea la integración de GitHub de Neon (consola de Neon → Integrations → GitHub).

## Más información

La arquitectura, las decisiones y la tabla de qué es real y qué es demostración están en
[`docs/ARQUITECTURA.md`](../docs/ARQUITECTURA.md).
