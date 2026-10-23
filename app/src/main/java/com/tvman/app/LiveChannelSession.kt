package com.tvman.app

/** In-process ordered channel queue used by the live player for CH+/CH− zapping. */
object LiveChannelSession {
    private var queue: List<Channel> = emptyList()
    private var activeUrl: String? = null

    @Synchronized
    fun setQueue(channels: List<Channel>, currentUrl: String) {
        queue = channels.distinctBy { it.url }
        activeUrl = currentUrl
    }

    @Synchronized
    fun step(currentUrl: String?, direction: Int): Channel? {
        if (queue.isEmpty()) return null
        val currentIndex = queue.indexOfFirst { it.url == (currentUrl ?: activeUrl) }
            .takeIf { it >= 0 }
            ?: queue.indexOfFirst { it.url == activeUrl }.coerceAtLeast(0)
        val step = if (direction >= 0) 1 else -1
        val nextIndex = (currentIndex + step + queue.size) % queue.size
        return queue[nextIndex].also { activeUrl = it.url }
    }
}
