# Decisiones del rediseño del frontend

Decisiones tomadas durante el rediseño y su motivo. Lo que hace cada pantalla está en
[`FRONTEND.md`](FRONTEND.md).

## Sobre la base

1. **Se parte de `feature/sin-comentarios`.** El rediseño toca casi todo el CSS y varias plantillas. Hacerlo
   sobre el código ya limpio evita conflictos con la rama que quita los comentarios.
2. **Sin frameworks de JavaScript.** Se mantienen Thymeleaf y TypeScript estricto compilado con `tsc`. El
   rediseño es de CSS y de comportamiento; no justifica cambiar de arquitectura.
3. **`static/js` sigue en el repositorio.** Así Maven y el despliegue no necesitan Node. Cada fase que
   toca `src/main/ts` incluye los `.js` recompilados en el mismo commit.
4. **Un commit por fase.** Cada fase se puede revisar y, si hace falta, revertir por separado.

## Fase 2: tokens y temas

5. **Todos los colores salen de un token.** Los valores escritos directamente en `styles.css` y
   `ai-chat.css` impedían mantener los tres temas. Ahora cada tema se define completo en `tokens.css`.
6. **Puntos de corte en 480, 768, 1024 y 1280 px.** Había `@media` con valores sueltos que no coincidían
   entre pantallas; se unificaron en cuatro, más 360 px como referencia del móvil más estrecho.
7. **Movimiento más corto (120, 180 y 240 ms).** Las transiciones anteriores se notaban lentas al navegar.
   Con `prefers-reduced-motion` se anulan.
8. **Campos a 16 px.** Por debajo de ese tamaño, Safari en iOS amplía la página al enfocar un campo.
9. **Claro, Oscuro y Sistema en lugar de Premium, Oscuro y Blanco y negro.** Es el patrón que la gente
   reconoce. Sistema es la opción predeterminada y no se guarda, así la página sigue al sistema operativo
   hasta que el usuario elige otra cosa.
10. **La paleta monocroma pasa a ser automática.** Solo se aplica con `prefers-contrast: more` y la opción
    Sistema. Como opción manual casi no se usaba y confundía.
11. **Migración de `premium` a `light`.** Quien tenía guardado el tema antiguo no ve un cambio brusco; los
    valores desconocidos se borran.
12. **`theme-color` antes de pintar.** `theme-init.js` lo fija junto a `data-theme` para que la barra del
    navegador móvil no muestre el color del tema anterior.

## Fase 3: inicio de sesión y registro

13. **En el móvil, primero el formulario.** La foto ocupaba la primera pantalla y había que desplazarse
    para empezar a escribir. Por debajo de 768 px se quita la foto y el marco de la tarjeta.
14. **Franja de foto en la tableta.** Entre 768 y 1023 px, dos mitades dejaban el formulario demasiado
    estrecho. Una franja corta conserva la marca sin quitarle espacio.
15. **Nombre y Apellido apilados por debajo de 480 px.** En dos columnas, los contadores y las pistas no
    cabían.
16. **Una sola mención de la longitud de la contraseña.** Aparecía en la pista y en la lista de reglas; ahora
    solo en la regla, marcada como obligatoria.
17. **El correo ya registrado se avisa una vez.** Antes aparecía en la pista de disponibilidad y en el error
    del campo; ahora solo como error, junto al campo.
18. **Un inicio de sesión rechazado quita las marcas de campo válido.** Mostrar marcas verdes junto a
    «No se pudo iniciar sesión. Comprueba tus credenciales.» se contradecía.

## Entorno local

19. **El perfil `demo` sirve todo sin caché.** `spring.thymeleaf.cache=false`,
    `spring.web.resources.cache.cachecontrol.no-store=true` y `spring.web.resources.chain.cache=false`,
    para que un cambio de diseño se vea al recargar. Producción conserva su caché.
20. **Hash de contenido solo en las hojas de estilo.** Los scripts son módulos ES que se importan entre sí
    por ruta relativa: con un nombre versionado, `app.js` se cargaría dos veces (una con hash desde la
    plantilla y otra sin hash desde los `import`). Al servirlos con `no-store`, siempre llega su última
    versión.
