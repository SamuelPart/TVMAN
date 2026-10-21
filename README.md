# TVMAN para Android TV

TVMAN toma como base funcional la rama [`arena/01a09a92-iptv`](https://github.com/SamuelPart/iptv/tree/arena/01a09a92-iptv) del proyecto IPTV (commit base `bed9c31`): gestión y filtrado de listas M3U, canales en vivo, reproducción, búsqueda, cine/series, favoritos, historial y ajustes. Este repositorio es la adaptación independiente para Android TV y móviles Android; la rama de referencia no se modifica.

## Abrir en Android Studio
1. Abrir la carpeta raíz de este repositorio.
2. Usar JDK 17 y sincronizar Gradle.
3. Ejecutar `app` en un emulador Android TV, televisor/dispositivo Android TV o teléfono Android compatible.

TVMAN está dirigido únicamente a Android TV: abre una interfaz de inicio de lienzo claro, navegación lateral y controles con foco para mando/DPAD. El manifiesto requiere `android.software.leanback`, publica solo el inicio TV y excluye teléfonos del filtro de dispositivos compatibles. La interfaz conecta con canales, cine/series, búsqueda y ajustes. El identificador es `com.tvman.app`.

## Anuncios
Los anuncios están desactivados en esta versión: no se inicializa AdMob, no se solicitan anuncios y la reproducción no tiene una puerta de anuncios.

## Alcance
La app hereda los flujos y funciones de la rama IPTV indicada. Esta adaptación todavía requiere una pasada de pruebas en controles remotos y tamaños de pantalla de TV. No incluye listas/canales licenciados; el usuario aporta sus propias fuentes M3U.
