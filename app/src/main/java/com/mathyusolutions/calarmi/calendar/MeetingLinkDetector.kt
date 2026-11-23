package com.mathyusolutions.calarmi.calendar

import android.net.Uri

enum class MeetingProvider(val displayName: String) {
    TEAMS("Teams"), ZOOM("Zoom"), GOOGLE_MEET("Google Meet")
}

data class MeetingLink(val provider: MeetingProvider, val url: String)

/**
 * Busca el enlace para unirse a una reunión de Teams, Zoom o Google Meet en la
 * ubicación y la descripción de un evento. Mismo criterio que en iOS: entre
 * varios enlaces del mismo servicio gana el que de verdad sirve para unirse.
 */
object MeetingLinkDetector {
    private val URL_REGEX = Regex("""https?://[^\s<>"')\]]+""", RegexOption.IGNORE_CASE)

    fun extract(location: String?, notes: String?, url: String? = null): MeetingLink? {
        val candidates = buildList {
            url?.let { link(it) }?.let(::add)
            for (text in listOf(location, notes)) {
                if (text.isNullOrEmpty()) continue
                URL_REGEX.findAll(text).forEach { m -> link(m.value.trimEnd('.', ',', ';'))?.let(::add) }
            }
        }
        return candidates.maxWithOrNull(compareBy<MeetingLink> { priority(it) }.thenByDescending { candidates.indexOf(it) })
    }

    fun provider(url: String): MeetingProvider? {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return null
        return when {
            matches(host, "teams.microsoft.com") || matches(host, "teams.live.com") -> MeetingProvider.TEAMS
            matches(host, "zoom.us") || matches(host, "zoom.com") -> MeetingProvider.ZOOM
            host == "meet.google.com" -> MeetingProvider.GOOGLE_MEET
            else -> null
        }
    }

    private fun link(url: String): MeetingLink? = provider(url)?.let { MeetingLink(it, url) }

    private fun matches(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun priority(link: MeetingLink): Int {
        val uri = Uri.parse(link.url)
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        return when (link.provider) {
            MeetingProvider.TEAMS -> when {
                path.startsWith("/meet/") || path.startsWith("/l/meetup-join") -> 2
                host.startsWith("visit.") || path.startsWith("/meetingoptions") -> 0
                else -> 1
            }
            MeetingProvider.ZOOM ->
                if (listOf("/j/", "/w/", "/s/", "/my/").any(path::startsWith)) 2 else 1
            MeetingProvider.GOOGLE_MEET -> if (path.length > 1) 2 else 1
        }
    }
}
