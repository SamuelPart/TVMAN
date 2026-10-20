# TVMAN para Android TV

TVMAN toma como base funcional la rama [`arena/01a09a92-iptv`](https://github.com/SamuelPart/iptv/tree/arena/01a09a92-iptv) del proyecto IPTV (commit base `bed9c31`): gestión y filtrado de listas M3U, canales en vivo, reproducción, búsqueda, cine/series, favoritos, historial y ajustes. Este repositorio es la adaptación independiente para Android TV y móviles Android; la rama de referencia no se modifica.

## Abrir en Android Studio
1. Abrir la carpeta raíz de este repositorio.
2. Usar JDK 17 y sincronizar Gradle.
3. Ejecutar `app` en un emulador Android TV, televisor/dispositivo Android TV o teléfono Android compatible.

El manifiesto añade `LEANBACK_LAUNCHER` para TV, mantiene el lanzador Android normal para teléfonos, declara el dispositivo táctil como opcional y usa el identificador de aplicación `com.tvman.app`. La interfaz principal queda libre para orientarse en móvil; el reproductor de vídeo mantiene orientación horizontal.

## Anuncios
Los anuncios están desactivados en esta versión: no se inicializa AdMob, no se solicitan anuncios y la reproducción no tiene una puerta de anuncios.

## Alcance
La app hereda los flujos y funciones de la rama IPTV indicada. Esta adaptación todavía requiere una pasada de pruebas en controles remotos y tamaños de pantalla de TV. No incluye listas/canales licenciados; el usuario aporta sus propias fuentes M3U.
