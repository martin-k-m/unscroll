package me.blinkdev.unscroll.block

/**
 * A "surface" is one short-video feed inside a host app, e.g. Reels inside Instagram.
 *
 * Detection is deliberately layered, because vendors rename view ids between releases:
 *   1. [viewIds]  exact resource ids, cheapest and most precise
 *   2. [texts]    visible text or content descriptions, used as a fallback
 * A surface matches when any single signal matches. That biases towards false positives,
 * which in practice means the app occasionally backs you out of something adjacent. Toggle
 * the surface off if that happens more than it helps.
 */
data class Surface(
    val id: String,
    val label: String,
    val packageName: String,
    val viewIds: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
    /** Whole-app blocks skip detection entirely and bounce you out on launch. */
    val wholeApp: Boolean = false,
)

object BlockRules {

    val surfaces: List<Surface> = listOf(
        Surface(
            id = "youtube_shorts",
            label = "YouTube Shorts",
            packageName = "com.google.android.youtube",
            viewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_fragment_root",
                "com.google.android.youtube:id/shorts_video_container",
            ),
            texts = listOf("Shorts"),
        ),
        Surface(
            id = "instagram_reels",
            label = "Instagram Reels",
            packageName = "com.instagram.android",
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_media_container",
                "com.instagram.android:id/clips_tab",
            ),
            texts = listOf("Reels"),
        ),
        Surface(
            id = "instagram_explore",
            label = "Instagram Explore",
            packageName = "com.instagram.android",
            viewIds = listOf("com.instagram.android:id/explore_grid"),
            texts = listOf("Explore"),
        ),
        Surface(
            id = "facebook_reels",
            label = "Facebook Reels",
            packageName = "com.facebook.katana",
            viewIds = listOf("com.facebook.katana:id/reels_viewer_root"),
            texts = listOf("Reels"),
        ),
        Surface(
            id = "snapchat_spotlight",
            label = "Snapchat Spotlight",
            packageName = "com.snapchat.android",
            viewIds = listOf(
                "com.snapchat.android:id/spotlight_view_pager",
                "com.snapchat.android:id/ngs_spotlight_icon_container",
            ),
            texts = listOf("Spotlight"),
        ),
        Surface(
            id = "reddit_video_feed",
            label = "Reddit video feed",
            packageName = "com.reddit.frontpage",
            viewIds = listOf("com.reddit.frontpage:id/video_container"),
            texts = listOf("Watch"),
        ),
        Surface(
            id = "tiktok_all",
            label = "TikTok (whole app)",
            packageName = "com.zhiliaoapp.musically",
            wholeApp = true,
        ),
    )

    /** Apps offered on the daily-limit screen, whether or not a surface rule exists. */
    val limitCandidates: List<Pair<String, String>> = listOf(
        "com.google.android.youtube" to "YouTube",
        "com.instagram.android" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.facebook.katana" to "Facebook",
        "com.snapchat.android" to "Snapchat",
        "com.reddit.frontpage" to "Reddit",
        "com.twitter.android" to "X",
        "com.linkedin.android" to "LinkedIn",
    )

    fun surfacesFor(packageName: String): List<Surface> =
        surfaces.filter { it.packageName == packageName }

    /**
     * The surface to block on the current screen, or null.
     *
     * The two signal lookups are passed in because reading them needs an accessibility node
     * tree. Rule strings are handed to them verbatim: whatever "matches" means for a text is
     * the caller's business, not this function's.
     */
    fun match(
        packageName: String,
        enabledSurfaceIds: Set<String>,
        hasViewId: (String) -> Boolean,
        hasSelectedText: (String) -> Boolean,
    ): Surface? {
        val candidates = surfacesFor(packageName).filter { it.id in enabledSurfaceIds }
        candidates.firstOrNull { it.wholeApp }?.let { return it }
        return candidates.firstOrNull { surface ->
            surface.viewIds.any(hasViewId) || surface.texts.any(hasSelectedText)
        }
    }

    fun labelFor(packageName: String): String =
        limitCandidates.firstOrNull { it.first == packageName }?.second ?: packageName
}
