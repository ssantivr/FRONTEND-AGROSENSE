# AgroSense · Frontend

Interfaz web de AgroSense, generada en el servidor con Java 21, Spring Boot, Spring MVC y Thymeleaf.
No usa Node.js ni frameworks de JavaScript.

## Ejecutar en modo demostración

No necesita base de datos: usa H2 en memoria con datos de ejemplo.

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

Las tablas deben existir y coincidir con las entidades (`ddl-auto=validate`).

## Pruebas

```bash
./mvnw clean verify
```

## Más información

La arquitectura, las decisiones y la tabla de qué es real y qué es demostración están en
[`docs/ARQUITECTURA.md`](../docs/ARQUITECTURA.md).
