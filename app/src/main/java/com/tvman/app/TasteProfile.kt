package com.tvman.app

/** Shared Spanish/English keyword classifier used by the catalog genre filters. */
object TasteProfile {
    private val genreKeys: Map<String, List<String>> = linkedMapOf(
        "acción" to listOf("accion", "acción", "action", "aventura", "combate", "batalla", "giro", "asalto"),
        "ciencia ficción" to listOf("ciencia ficción", "sci-fi", "scifi", "alien", "ovni", "robot", "nave espacial", "distop", "time travel", "viaje en el tiempo", "matrix", "espacial"),
        "comedia" to listOf("comedia", "comedy", "humor", "parodia", "romance cómic", "feel good"),
        "drama" to listOf("drama", "melodrama", "biopic", "basada en hechos", "true story"),
        "terror" to listOf("terror", "horror", "scary", "maldici", "poseída", "demonio", "zombi", "asusta", "jumpscare"),
        "thriller" to listOf("thriller", "suspenso", "suspense", "detective", "misterio", "asesin", "policía", "crimen", "mafia", "cartel"),
        "fantasía" to listOf("fantasía", "fantasy", "magia", "hechicero", "dragones", "dragón", "hadas", "épica medieval"),
        "animación" to listOf("animación", "animated", "animation", "anime", "dibujos", "pixar", "ghibli"),
        "romance" to listOf("romance", "romántic", "amor", "love story", "boda"),
        "familia" to listOf("familia", "familiar", "kids", "infantil", "niños", "navideña"),
        "histórica" to listOf("histórica", "historical", "época", "period", "siglo", "guerra mundial", "napole"),
        "música" to listOf("musical", "música", "concierto", "concert", "rap", "jazz"),
        "documental" to listOf("documental", "documentales", "documentary", "biografía", "biography", "naturaleza", "wildlife")
    )

    private val genreCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    fun genreKeysOf(media: CineMedia): List<String> {
        val key = "${media.title}|${media.group}"
        genreCache[key]?.let { return it }
        val searchableText = "${media.title} ${media.searchTitle} ${media.group} ${media.overview.orEmpty()}".lowercase()
        val matches = genreKeys.filterValues { terms -> terms.any { term -> searchableText.contains(term) } }.keys.toList()
        genreCache[key] = matches
        return matches
    }
}
