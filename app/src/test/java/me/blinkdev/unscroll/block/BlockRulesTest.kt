package me.blinkdev.unscroll.block

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val INSTAGRAM = "com.instagram.android"
private const val YOUTUBE = "com.google.android.youtube"
private const val TIKTOK = "com.zhiliaoapp.musically"
private const val EXPLORE_GRID = "com.instagram.android:id/explore_grid"

private val allSurfaces = BlockRules.surfaces.map { it.id }.toSet()

/** Matches on an exact set of signals. Nothing here models the platform's own text lookup. */
private fun match(
    packageName: String,
    enabled: Set<String> = allSurfaces,
    viewIds: Set<String> = emptySet(),
    selectedTexts: Set<String> = emptySet(),
): Surface? = BlockRules.match(packageName, enabled, viewIds::contains, selectedTexts::contains)

class BlockRulesMatchTest {

    @Test
    fun `explore matches on its own view id alone`() {
        assertEquals("instagram_explore", match(INSTAGRAM, viewIds = setOf(EXPLORE_GRID))?.id)
    }

    @Test
    fun `explore matches on a selected Explore label alone`() {
        assertEquals("instagram_explore", match(INSTAGRAM, selectedTexts = setOf("Explore"))?.id)
    }

    @Test
    fun `a screen with neither signal is not blocked`() {
        assertNull(match(INSTAGRAM))
    }

    @Test
    fun `explore does not match once its toggle is off`() {
        val withoutExplore = allSurfaces - "instagram_explore"
        assertNull(
            match(
                INSTAGRAM,
                enabled = withoutExplore,
                viewIds = setOf(EXPLORE_GRID),
                selectedTexts = setOf("Explore"),
            ),
        )
    }

    @Test
    fun `an Explore label in an app with no Explore rule is not blocked`() {
        listOf(YOUTUBE, "com.reddit.frontpage", "com.spotify.music", "com.android.settings")
            .forEach { pkg ->
                assertNull(match(pkg, selectedTexts = setOf("Explore")), pkg)
            }
    }

    @Test
    fun `the Explore view id in another app is not blocked`() {
        assertNull(match(YOUTUBE, viewIds = setOf(EXPLORE_GRID)))
    }

    @Test
    fun `an unknown package is never blocked whatever it shows`() {
        val everything = BlockRules.surfaces.flatMap { it.viewIds }.toSet()
        val labels = BlockRules.surfaces.flatMap { it.texts }.toSet()
        assertNull(match("com.example.app", viewIds = everything, selectedTexts = labels))
    }

    @Test
    fun `no surface matches when every toggle is off`() {
        assertNull(
            match(INSTAGRAM, enabled = emptySet(), viewIds = setOf(EXPLORE_GRID)),
        )
    }

    @Test
    fun `signals are queried verbatim, view ids before labels, in rule order`() {
        val viewIdQueries = mutableListOf<String>()
        val textQueries = mutableListOf<String>()
        BlockRules.match(
            INSTAGRAM,
            allSurfaces,
            { viewIdQueries += it; false },
            { textQueries += it; false },
        )

        val reels = BlockRules.surfaces.first { it.id == "instagram_reels" }
        assertEquals(reels.viewIds + EXPLORE_GRID, viewIdQueries)
        // No lowercased, trimmed or otherwise derived variant is ever asked for.
        assertEquals(listOf("Reels", "Explore"), textQueries)
    }

    @Test
    fun `each YouTube Shorts view id matches on its own`() {
        val shorts = BlockRules.surfaces.first { it.id == "youtube_shorts" }
        shorts.viewIds.forEach { id ->
            assertEquals("youtube_shorts", match(YOUTUBE, viewIds = setOf(id))?.id, id)
        }
    }

    @Test
    fun `a whole-app rule blocks with no signals present`() {
        assertEquals("tiktok_all", match(TIKTOK)?.id)
    }

    @Test
    fun `a whole-app rule that is off does not block`() {
        assertNull(match(TIKTOK, enabled = allSurfaces - "tiktok_all"))
    }

    @Test
    fun `TikTok is the only whole-app rule, so every other surface is on by default`() {
        assertEquals(listOf("tiktok_all"), BlockRules.surfaces.filter { it.wholeApp }.map { it.id })
    }
}

class BlockRulesDataTest {

    @Test
    fun `surface ids are unique`() {
        val ids = BlockRules.surfaces.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `label signals are unique within a package, so match order does not decide the outcome`() {
        BlockRules.surfaces.groupBy { it.packageName }.forEach { (pkg, group) ->
            val texts = group.flatMap { it.texts }
            assertEquals(texts.size, texts.toSet().size, pkg)
        }
    }

    @Test
    fun `every view id is namespaced to its own package`() {
        BlockRules.surfaces.forEach { surface ->
            surface.viewIds.forEach { id ->
                assertTrue(id.startsWith("${surface.packageName}:id/"), id)
            }
        }
    }

    @Test
    fun `a rule without a whole-app block carries at least one signal`() {
        BlockRules.surfaces.filterNot { it.wholeApp }.forEach { surface ->
            assertTrue(surface.viewIds.isNotEmpty() || surface.texts.isNotEmpty(), surface.id)
        }
    }

    @Test
    fun `surfacesFor returns only that package`() {
        assertEquals(
            listOf("instagram_reels", "instagram_explore"),
            BlockRules.surfacesFor(INSTAGRAM).map { it.id },
        )
        assertEquals(emptyList<Surface>(), BlockRules.surfacesFor("com.example.app"))
    }

    @Test
    fun `labelFor falls back to the package name`() {
        assertEquals("Instagram", BlockRules.labelFor(INSTAGRAM))
        assertEquals("com.example.app", BlockRules.labelFor("com.example.app"))
    }
}
