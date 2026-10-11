# Créditos de marca, fotografías y tipografía

## Logotipo
Símbolo (hoja en dos tonos partida por una nervadura en forma de circuito, con tres nodos de sensor) y variantes de AgroSense (`agrosense-*.svg`, `favicon.svg`): diseño propio del proyecto.
En la interfaz el símbolo se dibuja en línea (`templates/fragments/brand.html`) para poder animarlo y
adaptarlo al tema; los archivos de esta carpeta son para documentos, impresión y el favicon.
Los logos horizontales usan texto con la fuente Inter: fuera de la aplicación dependen de que la fuente
esté instalada.

## Fotografías (`../photos/`)
Obtenidas de Wikimedia Commons, redimensionadas a 960 y 1600 px de ancho y recomprimidas en JPEG.
Las licencias CC BY y CC BY-SA exigen citar al autor: la aplicación lo hace bajo la foto del inicio de
sesión y del registro, y en Configuración → Fotografías.

| Archivo | Lugar | Autor | Licencia | Origen |
|---|---|---|---|---|
| `coffee-*.jpg` | Chinchiná, Caldas | Ricohin | CC BY 3.0 | https://commons.wikimedia.org/wiki/File:Chinchin%C3%A1_cafetal.jpg |
| `narino-fields-*.jpg` | Tajumbina, Nariño | Jeyson Diaz | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Cultivos_en_Tajumbina.jpg |
| `plantain-*.jpg` | Buga, Valle del Cauca | Samuel Rengifo | CC BY-SA 4.0 | https://commons.wikimedia.org/wiki/File:Plataneras_en_la_zona_rural_de_Buga.JPG |
| `pivot-*.jpg` | Montana, EE. UU. | Lance Cheung, USDA | Dominio público | https://commons.wikimedia.org/wiki/File:Irrigation_and_Water_(20190829-NRCS-LSC-1092).jpg |

Las miniaturas de mapa y el mapa del inicio usan teselas de © colaboradores de OpenStreetMap (ODbL).
La vista «Satélite» del mapa usa World Imagery de Esri (© Esri, Maxar, Earthstar Geographics), servida
desde `server.arcgisonline.com`. Su uso continuado en producción está sujeto a los términos de Esri, que
piden una cuenta de ArcGIS; para la demostración se usa el servicio público.
## Tipografía (`../../fonts/`)
Plus Jakarta Sans (variable, pesos 200 a 800, subconjunto latino), de Tokotype — SIL Open Font License 1.1 —
https://fonts.google.com/specimen/Plus+Jakarta+Sans

## Capturas (`../screens/`)
Pantallas reales de la aplicación con la cuenta de demostración, usadas en la portada pública. Hay que
volver a generarlas cuando cambie el diseño.
