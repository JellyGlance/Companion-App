package dev.nerdytech.jellyglance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures follow apps/api/classes/command-center.js in the JellyGlance server. */
class FieldsTest {
    private val nowPlaying = mapOf(
        "jellyglance" to true, "playing" to 1.0, "transcoding" to 0.0,
        "items" to listOf(mapOf("sessionId" to "abc", "viewer" to "sam", "title" to "The Grand Tour (2016) · Last Car Standing", "client" to "Jellyfin Web", "device" to "Chrome", "method" to "DirectPlay")),
        "updatedAt" to "2026-11-26T13:00:00.000Z",
    )

    @Test fun nowPlayingHeadlineAndHiddenKeys() {
        val flat = Fields.flatten(nowPlaying)
        assertTrue(flat.leaves.none { it.key == "jellyglance" || it.key == "updatedAt" })
        val h = Fields.headline(flat, Catalogue.get("nowplaying")!!, null)
        assertEquals("1", h.value)
        assertEquals("Streaming now", h.caption)
    }

    @Test fun typedNowPlaying() {
        val np = Models.nowPlaying(nowPlaying)
        assertEquals(1, np.playing)
        val s = np.streams.single()
        assertEquals("sam", s.viewer)
        assertEquals("Chrome", s.device)
        assertEquals("Direct play", s.methodLabel)
        assertFalse(np.live)
    }

    @Test fun liveSessionsGiveProgressAndPoster() {
        val raw = listOf(
            mapOf("Id" to "s1", "UserName" to "kim", "Client" to "Android TV", "DeviceName" to "Shield",
                "NowPlayingItem" to mapOf("Id" to "ep1", "Type" to "Episode", "SeriesName" to "Andor", "SeriesId" to "ser1", "Name" to "Rix Road",
                    "ParentIndexNumber" to 1.0, "IndexNumber" to 12.0, "RunTimeTicks" to 400.0),
                "PlayState" to mapOf("PositionTicks" to 100.0, "IsPaused" to true, "PlayMethod" to "Transcode")),
            mapOf("Id" to "idle", "UserName" to "x"),
        )
        val np = Models.liveSessions(raw)!!
        assertEquals(1, np.playing)
        assertEquals(1, np.transcoding)
        val s = np.streams.single()
        assertEquals("Andor", s.title)
        assertEquals("S1E12 · Rix Road", s.subtitle)
        assertEquals("ser1", s.posterId)
        assertEquals(0.25f, s.progress!!, 0.001f)
        assertTrue(s.paused)
    }

    @Test fun watchUsesSecondsAsDuration() {
        val flat = Fields.flatten(mapOf("hoursToday" to 1.5, "secondsToday" to 5400.0, "secondsWeek" to 0.0))
        val h = Fields.headline(flat, Catalogue.get("watch")!!, null)
        assertEquals("1h 30m", h.value)
        assertEquals("Watched today", h.caption)
    }

    @Test fun storagePrefersLabel() {
        val h = Fields.headline(Fields.flatten(mapOf("totalLabel" to "4.0 TB", "totalBytes" to 4.4e12)), Catalogue.get("storage")!!, null)
        assertEquals("4.0 TB", h.value)
        assertEquals("Library size", h.caption)
    }

    @Test fun healthCamelCaseFlagIsAlert() {
        val def = Catalogue.get("health")!!
        val flat = Fields.flatten(mapOf("digestOk" to true, "digest" to 0.0, "jellyfinOk" to false, "jellyfinVersion" to "10.11.2"))
        val h = Fields.headline(flat, def, null)
        assertEquals("Down", h.value)
        assertEquals(Level.ALERT, Fields.overallLevel(flat, def, h))
        assertFalse(Models.health(mapOf("jellyfinOk" to false, "digestOk" to true)).ok)
    }

    @Test fun requestsAndDownloads() {
        val r = Models.requests(mapOf("pending" to 1.0, "items" to listOf(mapOf("id" to 42.0, "sourceId" to "a", "title" to "Dune", "status" to 1.0, "requestedBy" to "sam"))))
        assertEquals("42", r.items.single().id)
        assertTrue(r.items.single().pending)
        val d = Models.downloads(mapOf("active" to 1.0, "items" to listOf(mapOf("id" to "h", "name" to "x", "state" to "paused", "progress" to 50.0))))
        assertEquals(0.5f, d.items.single().progress, 0.001f)
        assertTrue(d.items.single().paused)
    }

    @Test fun warnPositive() {
        val def = Catalogue.get("stalled")!!
        assertEquals(Level.WARN, Fields.headline(Fields.flatten(mapOf("stalled" to 3.0)), def, null).level)
        assertEquals(Level.OK, Fields.headline(Fields.flatten(mapOf("stalled" to 0.0)), def, null).level)
    }

    @Test fun formatting() {
        assertEquals("1.5 GB", Fields.formatNumber(1.5 * 1024 * 1024 * 1024, "totalBytes"))
        assertEquals("1d 2h", Fields.formatNumber(26.0, "hours"))
        assertEquals("Last backup", Fields.humanize("backupAt"))
        assertEquals("Last backup at", Fields.humanize("lastBackup_at"))
        assertTrue(Fields.formatString("2020-01-01T10:00:00Z").endsWith("2020"))
        assertEquals("1:02:03", Models.ticksToClock(37230000000L))
    }

    @Test fun calendarSplitsPastAndFuture() {
        val now = java.time.LocalDate.now()
        val rel = Models.fullCalendar(mapOf("releases" to listOf(
            mapOf("title" to "Old", "date" to now.minusDays(30).toString() + "T10:00:00Z", "hasFile" to false),
            mapOf("title" to "Got it", "date" to now.minusDays(3).toString() + "T10:00:00Z", "hasFile" to true),
            mapOf("title" to "Later", "date" to now.plusDays(5).toString() + "T10:00:00Z", "subtitle" to "S01E02 - Pilot"),
            mapOf("title" to "Soon", "date" to now.plusDays(1).toString() + "T10:00:00Z", "subtitle" to "Movie release", "posterUrl" to "/proxy/Arr/Images/a?path=x"),
        )))
        val (upcoming, missed) = Models.splitReleases(rel)
        assertEquals(listOf("Soon", "Later"), upcoming.map { it.title })
        assertEquals("", upcoming[0].subtitle)
        assertEquals("S01E02 - Pilot", upcoming[1].subtitle)
        assertEquals(listOf("Old"), missed.map { it.title })
        assertEquals("http://h/proxy/Arr/Images/a?path=x", Models.absolute("http://h/", upcoming[0].posterUrl))
    }

    @Test fun requestTitles() {
        assertFalse(Models.requests(mapOf("items" to listOf(mapOf("id" to 1.0, "title" to "Request", "status" to 2.0)))).items.single().hasTitle)
        val full = Models.fullRequests(mapOf("requests" to listOf(
            mapOf("requestId" to 7.0, "sourceId" to "s", "title" to "Dune", "status" to "Pending", "createdAt" to "2026-11-01T00:00:00Z", "posterUrl" to "https://image.tmdb.org/t/p/w342/x.jpg"),
            mapOf("requestId" to 8.0, "title" to "Andor", "status" to "Available", "createdAt" to "2026-11-20T00:00:00Z"),
        )))
        assertEquals(listOf("Andor", "Dune"), full.map { it.title })
        assertTrue(full[1].pending)
        assertEquals("7", full[1].id)
    }

    @Test fun itemGlance() {
        val g = Models.glance(mapOf(
            "item" to mapOf("id" to "i1", "name" to "Dune", "type" to "Movie", "year" to 2021.0, "library" to "Movies"),
            "request" to mapOf("id" to 3.0, "title" to "Dune", "status" to 5.0, "source" to "Jellyseerr"),
            "download" to null, "arr" to mapOf("title" to "Dune", "service" to "Radarr", "hasFile" to true),
            "tdarr" to mapOf("connected" to true, "name" to "Tdarr"), "kometa" to mapOf("connected" to false, "name" to "Kometa"),
        ))!!
        assertEquals("Dune", g.name)
        assertEquals("Available", g.request!!.statusLabel)
        assertEquals(null, g.download)
        assertTrue(g.arr!!.hasFile)
        assertEquals(listOf("Tdarr"), g.tools)
        assertEquals(null, Models.glance(mapOf("error" to "x")))
    }

    @Test fun activityAndStats() {
        val plays = Models.activity(mapOf("items" to listOf(mapOf("viewer" to "sam", "title" to "Andor", "method" to "Transcode", "client" to "Web", "playedAt" to "2026-11-26T10:00:00Z"))))
        assertEquals("sam", plays.single().viewer)
        val st = Models.statistics(mapOf("items" to listOf(mapOf("title" to "Dune", "viewers" to 3.0)), "people" to listOf(mapOf("name" to "kim", "plays" to 12.0))))
        assertEquals(3, st.titles.single().viewers)
        assertEquals(12, st.people.single().plays)
        val u = Models.users(mapOf("users" to 4.0, "activeToday" to 1.0, "items" to listOf(mapOf("name" to "Roffo", "admin" to true))))
        assertTrue(u.items.single().admin)
    }

    @Test fun webhookIssuesGroupAndRecover() {
        val log = Issues.deliveries(listOf(
            mapOf("webhookId" to 3.0, "name" to "Discord", "webhookType" to "discord", "eventType" to "playback_started", "ok" to false, "error" to "timeout of 10000ms exceeded", "timestamp" to "2026-11-25T10:00:00Z"),
            mapOf("webhookId" to 3.0, "name" to "Discord", "eventType" to "playback_ended", "ok" to false, "status" to 429.0, "error" to "rate limited", "timestamp" to "2026-11-25T11:00:00Z"),
            mapOf("webhookId" to 4.0, "name" to "Ntfy", "ok" to false, "error" to "x", "timestamp" to "2026-11-24T10:00:00Z"),
            mapOf("webhookId" to 4.0, "name" to "Ntfy", "ok" to true, "timestamp" to "2026-11-24T12:00:00Z"),
        ))!!
        val health = Models.health(mapOf("digestOk" to false, "items" to listOf(
            mapOf("type" to "webhook", "label" to "Discord delivery failed"), mapOf("type" to "audit", "label" to "backup.failed"),
        )))
        val all = Issues.build(health, log, null)
        val discord = all.single { it.kind == Issue.Kind.WEBHOOK }
        assertEquals(2, discord.count)
        assertEquals("HTTP 429: rate limited", discord.summary)
        assertEquals("3", discord.webhookId)
        assertTrue(all.any { it.kind == Issue.Kind.AUDIT })
        // Dismissing hides it until a newer failure arrives.
        val dismissed = mapOf(discord.key to discord.latestMs)
        assertFalse(Issues.visible(all, dismissed).contains(discord))
        assertTrue(Issues.visible(all, mapOf(discord.key to discord.latestMs - 1)).contains(discord))
        // Without the log, digest lines are grouped with a count.
        val fallback = Issues.build(Models.health(mapOf("items" to List(8) { mapOf("type" to "webhook", "label" to "Discord delivery failed") })), null, null)
        assertEquals(8, fallback.single().count)
    }

    @Test fun onlyJellyGlanceOwn401SignsOut() {
        assertTrue(JellyGlanceApi.isOwnAuthFailure("""{"message":"Invalid token"}"""))
        assertTrue(JellyGlanceApi.isOwnAuthFailure("""{"message":"Authentication failed. No token or API key provided."}"""))
        assertTrue(JellyGlanceApi.isOwnAuthFailure(""))
        assertFalse(JellyGlanceApi.isOwnAuthFailure("""{"error":"Request failed with status code 401"}"""))
        assertFalse(JellyGlanceApi.isOwnAuthFailure("""{"error":"Unable to load Wizarr invites"}"""))
    }
}
