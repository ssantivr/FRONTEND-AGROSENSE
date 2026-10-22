# Pendientes fuera del frontend

Cambios que este repositorio no puede hacer solo porque tocan el backend (BACKEND-AGROSENSE), la base de datos
(BASE-DE-DATOS-AGROSENSE) o sus contratos. Ninguno se aplicó; cada uno dice qué haría falta.

## Seguridad

### Secreto JWT compartido
El frontend firma sus propios tokens para llamar al backend (`BackendToken`, con `BACKEND_JWT_SECRET`, que debe
ser igual a `JWT_SECRET` del backend). Quien tenga ese secreto puede hacerse pasar por cualquier usuario ante el
backend, y el secreto vive en dos servicios.

Propuesta: que el backend emita el token al iniciar sesión (`POST /api/auth/login` ya valida la contraseña), el
frontend lo guarde en la sesión del usuario (tabla `spring_session`) y lo envíe en cada llamada; al caducar,
volver a pedirlo o cerrar la sesión. Así el frontend deja de conocer `JWT_SECRET`.
Requiere: que `/api/auth/login` devuelva el token y su caducidad (comprobar el contrato en `api/openapi.json`).

### IP real en el límite de intentos de inicio de sesión
Todas las peticiones de inicio de sesión le llegan al backend desde la dirección del frontend, por eso hoy se
usa `LOGIN_MAX_ATTEMPTS_PER_ADDRESS=0` en Render. `AuthController` usa `httpRequest.getRemoteAddr()` y el backend
no tiene `server.forward-headers-strategy`, así que una cabecera `X-Forwarded-For` enviada por el frontend se
ignoraría. Además, confiar en esa cabecera sin restringir de quién viene permitiría falsear la IP.

Propuesta para el backend:
1. `server.forward-headers-strategy=native` y `server.tomcat.remoteip.internal-proxies` (o
   `trusted-proxies`) limitado a los proxies de Render y a la salida del frontend.
2. Entonces el frontend puede reenviar la IP del visitante en `X-Forwarded-For` desde `BackendClient.signIn` y
   `register`, y volver a activar `LOGIN_MAX_ATTEMPTS_PER_ADDRESS`.

### `GET /api/auth/check-user` es público y sin límite
Responde si un correo está registrado sin autenticación ni límite de peticiones (`SecurityConfig` del backend,
`permitAll`). Permite enumerar cuentas. Propuesta: límite por dirección (mismo mecanismo que el inicio de
sesión, una vez resuelta la IP real) y respuesta con retardo constante.

## Validaciones del registro
El backend (`RegisterRequest`) limita nombre y apellido a 2–18 caracteres y la contraseña a 8–32. Un apellido
compuesto como «Rodríguez de la Vega» (20) no cabe, y 32 es corto para frases de contraseña.

Lo que ya hace el frontend: no corta en silencio lo escrito o pegado (antes `maxlength` truncaba una
contraseña pegada y la cuenta quedaba con otra distinta), cuenta sin espacios repetidos ni de los extremos,
muestra contador y un mensaje que explica el límite.

Para subirlos (por ejemplo nombre/apellido a 60 y contraseña a 72, el máximo útil de BCrypt) hay que cambiar a la
vez `RegisterRequest` del backend, `RegistrationForm.MAX_*` y `LIMITS` en `src/main/ts/forms.ts`, y comprobar el
tamaño de las columnas `users.name` y `users.last_name` en `schema.sql`.

## Base de datos
- **Migraciones:** el esquema vive en `schema.sql` y se clona como carpeta hermana `../database` en backend y
  frontend; los cambios a bases existentes son SQL numerado que se ejecuta a mano (`migrations/`). Propuesta:
  Flyway en el backend (único dueño del esquema), con `V1__esquema_inicial.sql` a partir del `schema.sql` actual y
  las migraciones existentes como versiones siguientes; el frontend solo validaría (`ddl-auto=validate`).
- **README de la base de datos:** conserva rutas del diseño anterior del backend (`../../database` desde
  `backend/agrosense`; hoy el proyecto Maven está en la raíz de BACKEND-AGROSENSE) y una sección de diagnóstico de
  PostgreSQL local que ya no aplica al despliegue en Neon. Los enlaces relativos a `docs/` resuelven fuera del
  repositorio en GitHub (`blob/docs/...`). Corregirlo en BASE-DE-DATOS-AGROSENSE.

## Doble camino de datos (API y base de datos)
El frontend lee casi todo directamente de la base de datos compartida y usa la API para iniciar sesión, crear
cuentas, atender alertas, registrar sensores y riegos, y las lecturas de la gráfica. La decisión está en un solo
punto (`BackendClient.isAvailable()`) y la interfaz muestra cuándo se usa la base de datos directamente. Las
entidades JPA están duplicadas en ambos servicios.

Propuesta: que la API cubra todas las lecturas que necesita la interfaz (listados de parcelas, cultivos,
sensores con su última lectura, alertas, riegos e historial de lecturas por intervalo de fechas, no solo por
número de lecturas) y retirar después el acceso directo del frontend a la base de datos y sus entidades. Hasta
entonces no se elimina el respaldo.

## Servicio de IA tras dormir Render
El servicio de predicción (`ia-agrosense`) también duerme en el plan gratuito y su primera respuesta tarda más de
lo que esperan algunos clientes. En este espacio de trabajo el backend usa `AI_SERVICE_TIMEOUT_MS=30000` por
defecto y en producción se configuró 60000 con `AI_SERVICE_MAX_ATTEMPTS=2`; si la versión publicada del backend
mantiene un tiempo de espera de 5 s, debe alinearse con esos valores o despertar el servicio con una petición a
`/health` antes de la predicción.

## Historial de lecturas por intervalo
`GET /api/sensors/{id}/readings` solo acepta `limit` (máximo 500). Para los periodos de 3 y 7 días de la gráfica,
si esas 500 lecturas no cubren el periodo, el frontend las lee de la base de datos y lo indica como «base de
datos de respaldo». Propuesta: parámetros `from`/`to` en ese endpoint.

## Control de riego
El backend registra riegos, no acciona bombas ni válvulas: la interfaz lo dice y no simula un estado de bomba.
Cuando exista un endpoint que confirme el estado real del actuador, la animación de riego activo debería
depender de esa confirmación y no de la hora de fin calculada.
