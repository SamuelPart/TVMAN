# TVMAN para Android TV

TVMAN se basa funcionalmente en la rama [`arena/01a09a92-iptv`](https://github.com/SamuelPart/iptv/tree/arena/01a09a92-iptv) del proyecto IPTV (commit `bed9c31`): listas M3U, canales en vivo, reproducción, búsqueda, cine/series, favoritos, historial y ajustes. Este repositorio contiene la adaptación para Android TV; la rama de referencia no se modifica.

## Interfaz y controles
- Aplicación exclusiva para Android TV; requiere `android.software.leanback` y no ofrece lanzador para teléfonos.
- Inicio TV de lienzo claro, menú lateral y navegación con control remoto/DPAD.
- Todas las pantallas se fuerzan a horizontal y reciben tratamiento de foco remoto para botones y elementos interactivos.
- Las pantallas de video conservan su presentación de reproducción.

## Abrir y ejecutar
1. Abrir esta carpeta raíz en Android Studio.
2. Usar JDK 21 (o JDK 17) para Gradle y sincronizar el proyecto.
3. Crear/iniciar un emulador Android TV desde **Device Manager** y ejecutar `app`.

Identificador de aplicación: `com.tvman.app`.

## Anuncios y contenido
Los anuncios están desactivados: no se inicializa AdMob ni se solicitan anuncios; reproducir no requiere ver anuncios. TVMAN no incluye canales licenciados; el usuario configura sus propias fuentes M3U.
