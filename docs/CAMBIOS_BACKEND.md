# Cambios que el rediseño pide al backend

## Fases 2 y 3: ninguno

Los tokens, los temas y las pantallas de inicio de sesión y registro solo cambian plantillas, CSS y
TypeScript. No se añadió ni se modificó ninguna llamada al backend, ningún contrato ni ninguna tabla.

## Llamadas al backend que usan las pantallas rediseñadas

Ya existían y el rediseño no las cambia. Las hace el servidor del frontend (`BackendClient`), nunca el
navegador.

| Pantalla | Llamada | Para qué |
|----------|---------|----------|
| Registro | `GET /api/auth/check-user?email=` | Avisar mientras se escribe que el correo ya está registrado |
| Registro | `POST /api/auth/register` | Crear la cuenta |
| Inicio de sesión | `POST /api/auth/login` | Comprobar las credenciales y aplicar el límite de intentos |

Sin `BACKEND_URL` y `BACKEND_JWT_SECRET`, o mientras el backend no responda, las tres operaciones se hacen
contra la base de datos, como antes.

## Fases 4 a 7

Pendientes. Si alguna pantalla necesita un dato o una operación nuevos del backend, se anotará aquí con la
llamada, el formato de la respuesta y el motivo, antes de pedirlo en el repositorio del backend.
