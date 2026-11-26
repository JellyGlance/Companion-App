package dev.nerdytech.jellyglance.data

/** What we could work out from text another app shared: a search query plus any ids or year. */
data class SharedTitle(val query: String, val year: String?, val tmdbId: Int?, val mediaType: String?, val imdbId: String?) {
    companion object {
        private val url = Regex("""https?://\S+""")
        private val tmdb = Regex("""themoviedb\.org/(movie|tv)/(\d+)""")
        private val imdb = Regex("""\b(tt\d{6,10})\b""")
        private val year = Regex("""\((\d{4})\)""")

        fun parse(text: String): SharedTitle {
            val t = tmdb.find(text)
            val links = url.findAll(text).map { it.value }.toList()
            var q = url.replace(text, " ")
            // Common share wrappers: "Check out X on IMDb", "X - IMDb", "X | Letterboxd", "Watch X on Netflix".
            q = q.replace(Regex("""(?i)check out\s+"""), "")
                .replace(Regex("""(?i)\s+on\s+(IMDb|Letterboxd|TMDB|The Movie Database|Netflix|Prime Video|Disney\+?|Trakt|JustWatch)\b.*"""), "")
                .replace(Regex("""(?i)\s*[-|–—]\s*(IMDb|Letterboxd|TMDB|The Movie Database.*|Trakt|JustWatch|Rotten Tomatoes).*"""), "")
                .replace(Regex("""(?i)^watch\s+"""), "")
                .replace(Regex("""(?i)\b(film|movie|tv series|mini series|tv mini series)\b"""), " ")
                .replace(Regex("""⭐.*"""), "")
            val y = year.find(q)?.groupValues?.get(1)
            q = q.replace(year, " ").replace(Regex("""[\r\n"“”]+"""), " ").replace(Regex("""\s{2,}"""), " ").trim(' ', '-', '|', ':', '·', '.')
            // Letterboxd: the title is in the path (boxd.it short links aren't), e.g. /film/the-matrix/.
            if (q.isBlank()) links.firstNotNullOfOrNull { Regex("""letterboxd\.com/film/([a-z0-9-]+)""").find(it)?.groupValues?.get(1) }
                ?.let { q = it.replace('-', ' ') }
            return SharedTitle(q.take(80), y, t?.groupValues?.get(2)?.toIntOrNull(), t?.groupValues?.get(1), imdb.find(text)?.value)
        }
    }
}
