# AgroSense · Frontend

Interfaz web de AgroSense, generada en el servidor con Java 21, Spring Boot, Spring MVC y Thymeleaf.
No usa Node.js ni frameworks de JavaScript.

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
| `BACKEND_URL`           | Dirección de la API del backend, por ejemplo `https://backend-agrosense-oyhq.onrender.com`. Opcional |
| `BACKEND_JWT_SECRET`    | El mismo valor que `JWT_SECRET` del backend. Opcional   |

Con `BACKEND_URL` y `BACKEND_JWT_SECRET` definidas, atender alertas, registrar sensores y riegos y la
gráfica de lecturas pasan por la API del backend; sin ellas, o mientras el backend no responda, todo se
hace contra la base de datos.

Las tablas se crean con `../database/schema.sql` (ver [`database/README.md`](../database/README.md));
con `SQL_INIT_MODE=always` la aplicación lo ejecuta al arrancar.

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
