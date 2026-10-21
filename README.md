# TVMAN para Android TV

TVMAN es una app exclusiva para Android TV basada funcionalmente en la rama [`arena/01a09a92-iptv`](https://github.com/SamuelPart/iptv/tree/arena/01a09a92-iptv) del proyecto IPTV (commit `bed9c31`). Conserva listas M3U, canales en vivo, reproducción, cine/series, búsqueda, favoritos, historial y ajustes.

## Interfaz TV
- Inicio a pantalla completa y de borde a borde: sin panel enmarcado ni resumen intermedio.
- Muestra una pantalla de carga mientras prepara el catálogo y el arte TMDB; el título destacado y los pósteres usan `backdropUrl` / `posterUrl` del catálogo.
- Todas las pantallas se fuerzan a horizontal y los controles interactivos reciben foco para mando/DPAD.
- Android TV/Leanback es requisito; no se publica un lanzador para teléfonos.

## Abrir y ejecutar
1. Abrir la raíz del proyecto en Android Studio.
2. Usar JDK 21 o 17 para Gradle y sincronizar.
3. Iniciar un emulador Android TV desde Device Manager y ejecutar `app`.

Identificador: `com.tvman.app`. Los anuncios están desactivados. TVMAN no distribuye canales licenciados; configura tus propias fuentes M3U.
