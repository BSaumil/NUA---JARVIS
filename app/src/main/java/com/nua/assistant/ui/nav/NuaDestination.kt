package com.nua.assistant.ui.nav

/**
 * The five top-level destinations (`#38`). Replaces the pile of boolean `show*` flags the
 * UI previously navigated with — those made every new screen another flag and another
 * early `return`, and gave the user no sense of where they were.
 *
 * The split is by *verb*, not by feature: what's happening now (Home), asking NUA
 * something (Ask), what NUA knows (Memory), what NUA can do and has done (Act), and
 * settings about you and NUA's behaviour (You).
 */
enum class NuaDestination(val label: String, val contentDescription: String) {
    HOME("Home", "Home — what needs you now"),
    ASK("Ask", "Ask — chat with NUA"),
    MEMORY("Memory", "Memory — what NUA knows"),
    ACT("Act", "Act — what NUA can do and has done"),
    YOU("You", "You — settings and NUA's behaviour"),
}

/**
 * Sub-screens under [NuaDestination.MEMORY]. Nested rather than top-level because they're
 * all views onto the same thing — what NUA knows — and promoting each to its own tab
 * would make the bottom bar a menu instead of a map.
 */
enum class MemorySection(val label: String) {
    SEARCH("Search"),
    TIMELINE("Timeline"),
    DOCUMENTS("Documents"),
}

/**
 * The information-hierarchy labels (`#40`). Every piece of content NUA shows should be
 * one of these, so the user learns the vocabulary once and can then read any screen.
 * Deliberately a closed set: if something doesn't fit one of these seven, that's a signal
 * the content itself is unclear, not that the list needs an eighth entry.
 */
enum class InfoLabel(val label: String) {
    /** Happening right now, or already overdue. */
    NOW("Now"),

    /** Coming up today. */
    NEXT("Next"),

    /** Beyond today — scheduled, but not pressing. */
    LATER("Later"),

    /** Something NUA knows about you. */
    MEMORY("Memory"),

    /** Something NUA worked out by connecting things — a Dream. */
    INSIGHT("Insight"),

    /** Something NUA did, or is offering to do. */
    ACTION("Action"),

    /** Something wrong that needs attention. */
    ALERT("Alert"),
}
