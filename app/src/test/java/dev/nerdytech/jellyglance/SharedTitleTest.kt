package dev.nerdytech.jellyglance

import dev.nerdytech.jellyglance.data.SharedTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTitleTest {
    @Test fun imdbApp() {
        val s = SharedTitle.parse("Check out Inception on IMDb https://m.imdb.com/title/tt1375666/")
        assertEquals("Inception", s.query); assertEquals("tt1375666", s.imdbId)
    }

    @Test fun imdbBrowserTitle() {
        val s = SharedTitle.parse("The Matrix (1999) - IMDb\nhttps://www.imdb.com/title/tt0133093/")
        assertEquals("The Matrix", s.query); assertEquals("1999", s.year)
    }

    @Test fun tmdbLink() {
        val s = SharedTitle.parse("https://www.themoviedb.org/tv/1399-game-of-thrones")
        assertEquals(1399, s.tmdbId); assertEquals("tv", s.mediaType)
    }

    @Test fun letterboxd() {
        assertEquals("Dune: Part Two", SharedTitle.parse("Dune: Part Two (2024) | Letterboxd https://boxd.it/abc").query)
        assertEquals("the matrix", SharedTitle.parse("https://letterboxd.com/film/the-matrix/").query)
    }

    @Test fun plainText() {
        assertEquals("Severance", SharedTitle.parse("Severance").query)
    }
}
