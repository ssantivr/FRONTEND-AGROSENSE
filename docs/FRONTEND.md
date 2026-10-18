# Frontend de AgroSense: rediseño

Estado de la interfaz tras el rediseño, en la rama `feature/rediseno-frontend`. Explica qué hay y cómo se
organiza. Los motivos están en [`DECISIONES.md`](DECISIONES.md), y lo que el rediseño necesita del backend,
en [`CAMBIOS_BACKEND.md`](CAMBIOS_BACKEND.md).

## Fases

| Fase | Contenido | Estado |
|------|-----------|--------|
| 1 | Limpieza previa: código sin comentarios (`feature/sin-comentarios`) | Hecha |
| 2 | Tokens de diseño unificados y temas claro, oscuro y del sistema | Hecha |
| 3 | Pantallas de inicio de sesión y registro para móvil, tableta y escritorio | Hecha |
| 4 a 7 | Resto de pantallas | Pendientes |

## Dónde está cada cosa

| Qué | Dónde |
|-----|-------|
| Tokens (colores, tipografía, espacios, radios, sombras, movimiento, puntos de corte) | `src/main/resources/static/css/tokens.css` |
| Componentes y pantallas | `src/main/resources/static/css/styles.css` |
| Ventana del asistente | `src/main/resources/static/css/ai-chat.css` |
| Selector de tema y cabecera común (`head-assets`, `theme-toggle`) | `src/main/resources/templates/fragments/brand.html` |
| Tema antes de pintar la página | `src/main/ts/theme-init.ts` |
| Cambio de tema, seguimiento del sistema y sincronización entre pestañas | `src/main/ts/theme.ts` |
| Validación de los formularios de acceso | `src/main/ts/login.ts`, `src/main/ts/forms.ts` |
| Muestrario del sistema de diseño | `/design-system` (`templates/design-system.html`) |

`static/js` es la salida de `tsc` y se guarda en el repositorio: después de cambiar `src/main/ts`,
ejecuta `npm run build` y confirma también los `.js`.

## Tokens (fase 2)

- **Tipografía:** Plus Jakarta Sans (`static/fonts`). Los campos de formulario usan `--text-input: 1rem`
  (16 px) para que iOS no amplíe la página al enfocarlos.
- **Puntos de corte:** `--bp-xs` 360, `--bp-sm` 480, `--bp-md` 768, `--bp-lg` 1024 y `--bp-xl` 1280 px.
  Las `@media` de `styles.css` usan solo esos valores.
- **Iconos:** `--icon-xs` a `--icon-xl` (14 a 32 px).
- **Foco:** `--focus-width` y `--focus-offset` para `:focus-visible`.
- **Áreas seguras:** `--safe-top/right/bottom/left` con `env(safe-area-inset-*)`.
- **Movimiento:** `--motion-fast` 120 ms, `--motion-base` 180 ms y `--motion-slow` 240 ms; con
  `prefers-reduced-motion` las transiciones se anulan.
- **Colores:** ningún color va escrito directamente en `styles.css` ni en `ai-chat.css`; todos salen de un
  token de `tokens.css`, definido para cada tema (`[data-theme='light']`, `[data-theme='dark']`,
  `[data-theme='mono']`).

## Temas (fase 2)

El selector tiene tres opciones: **Claro**, **Oscuro** y **Sistema**, que es la predeterminada.

- La preferencia se guarda en `localStorage` con la clave `agrosense.theme` (`light` o `dark`). La opción
  Sistema no guarda nada: borra la clave.
- Con Sistema, la página sigue `prefers-color-scheme` y cambia en cuanto cambia el sistema operativo.
- Con `prefers-contrast: more`, la opción Sistema usa la paleta monocroma (`mono`). Ya no se ofrece como
  opción en el selector.
- `theme-init.js` se carga de forma síncrona en `<head>` y fija `data-theme`, `data-theme-preference` y
  `<meta name="theme-color">` antes de pintar, así que no hay destello del tema equivocado.
- El valor antiguo `premium` se migra a `light`; cualquier otro valor desconocido se borra.
- Al cambiar el tema en una pestaña, las demás lo aplican (evento `storage`).

## Inicio de sesión y registro (fase 3)

| Ancho | Diseño |
|-------|--------|
| Menos de 768 px (móvil) | El formulario va primero, sin la foto y sin marco de tarjeta. Por debajo de 480 px, Nombre y Apellido se apilan |
| De 768 a 1023 px (tableta) | Una franja corta con la foto y el titular, y debajo la tarjeta del formulario |
| 1024 px o más (escritorio) | Dos mitades: la foto con el titular y las funciones, y el formulario |

Comportamiento:

- Etiquetas siempre visibles, `autocomplete` e `inputmode` correctos, `autocapitalize="words"` en Nombre y
  Apellido, y botón para mostrar u ocultar la contraseña.
- Las indicaciones de la contraseña ya no repiten la regla de longitud. El texto inicial invita a escribir y
  la lista de reglas dice «De 8 a 32 caracteres, los espacios cuentan (obligatorio)», «Mayúsculas y
  minúsculas», «Un número» y «Un símbolo o un espacio».
- Un correo ya registrado se avisa una sola vez, junto al campo, y se oculta la indicación de
  disponibilidad.
- Si el inicio de sesión se rechaza, los campos dejan de marcarse como válidos.

## Revisarlo en local

```powershell
.\run-local.ps1      # desde la raíz del workspace; usa el perfil demo
```

- http://localhost:8080/login y http://localhost:8080/registro, a 390, 768 y 1440 px, con los tres temas.
- http://localhost:8080/design-system, después de iniciar sesión, para ver los tokens.
- Cuenta de demostración: `demo@agrosense.co` / `agrosense`.

El perfil `demo` sirve las plantillas y los estáticos sin caché (`no-store`) y añade el hash del contenido a
las hojas de estilo, así que basta con recargar. El perfil de producción no cambia.

## Pruebas

`WebFlowTests` comprueba que `/login`, `/registro` y `/` ofrecen los temas claro, oscuro y del sistema
(no el monocromo) y cargan `theme-init.js`, y que los formularios de acceso tienen etiquetas visibles, las
pistas de teclado y el botón de mostrar contraseña.

```bash
./mvnw clean verify
npm run typecheck
npm run build
```
