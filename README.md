# TVMAN para Android TV

TVMAN es una app exclusiva para Android TV basada funcionalmente en la rama [`arena/01a09a92-iptv`](https://github.com/SamuelPart/iptv/tree/arena/01a09a92-iptv) del proyecto IPTV (commit `bed9c31`). Conserva listas M3U, canales en vivo, reproducción, cine/series, búsqueda, favoritos, historial y ajustes.

## Interfaz TV
- La entrada principal es una pantalla cinematográfica de streaming; no muestra un panel intermedio de resumen.
- El título destacado y las filas de póster se alimentan del catálogo de TVMAN y de sus imágenes TMDB (`backdropUrl` / `posterUrl`).
- Todas las pantallas se fuerzan a horizontal y los controles interactivos reciben foco para mando/DPAD.
- Android TV/Leanback es requisito; no se publica un lanzador para teléfonos.

## Abrir y ejecutar
1. Abrir la raíz del proyecto en Android Studio.
2. Usar JDK 21 o 17 para Gradle y sincronizar.
3. Iniciar un emulador Android TV desde Device Manager y ejecutar `app`.

Identificador: `com.tvman.app`. Los anuncios están desactivados. TVMAN no distribuye canales licenciados; configura tus propias fuentes M3U.
