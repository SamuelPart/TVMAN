# TVMAN para Android TV

TVMAN es una app exclusiva para Android TV basada funcionalmente en la rama [`arena/01a09a92-iptv`](https://github.com/SamuelPart/iptv/tree/arena/01a09a92-iptv) del proyecto IPTV (commit `bed9c31`). Conserva listas M3U, canales en vivo, reproducción, cine/series, búsqueda, favoritos, historial y ajustes.

## Interfaz TV
- Inicio cinematográfico a pantalla completa: fondo TMDB inmersivo, navegación lateral que se expande al enfocarla y filas que se recorren con el mando.
- El hero permite avanzar o retroceder entre títulos destacados con botones o DPAD, actualizando el arte, título y sinopsis.
- «Continuar viendo» se coloca bajo el hero cuando hay progreso guardado; sin progreso, las recomendaciones quedan como primera fila.
- Al enfocar pósteres aparece una ficha rápida; tras una pausa breve, la portada cambia al arte TMDB del título sin iniciar la reproducción.
- Incluye colecciones editoriales y una opción para activar, ocultar y reordenar filas desde el mando.
- Las fichas y secciones usan transiciones suaves coherentes con el estilo cinematográfico.
- Muestra una pantalla de carga mientras prepara el catálogo y el arte TMDB; el título destacado y los pósteres usan `backdropUrl` / `posterUrl` del catálogo.
- Todas las pantallas se fuerzan a horizontal; los controles reciben un contorno de foco visible, navegación DPAD y restauración del último elemento enfocado al volver.
- El reproductor responde a las teclas multimedia del mando y permite revelar/enfocar controles con DPAD; los controles no se ocultan mientras tengan el foco.
- La búsqueda usa campos de texto y teclado del sistema aptos para el mando; las acciones importantes incluyen descripciones accesibles.
- La portada incorpora «Continuar viendo» con avance guardado para películas y series.
- Android TV/Leanback es requisito; no se publica un lanzador para teléfonos.

## Abrir y ejecutar
1. Abrir la raíz del proyecto en Android Studio.
2. Usar JDK 21 o 17 para Gradle y sincronizar.
3. Iniciar un emulador Android TV desde Device Manager y ejecutar `app`.

Identificador: `com.tvman.app`. Los anuncios están desactivados. TVMAN no distribuye canales licenciados; configura tus propias fuentes M3U.
