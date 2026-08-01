package org.linphone.shiroikuma

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * shiroikuma-rindenwa fork — the letter-sectioned Contacts list.
 *
 * Ported from the sister address book (shiroikuma-renrakusaki, `helpers/JapaneseSort.kt` and
 * `adapters/ContactsAdapter`), so both apps bucket and fold contacts identically.
 *
 * Sort keys starting with kana bucket into gojūon rows (あ か さ た な は ま や ら わ — voiced,
 * semi-voiced and small kana fold into their base row, ん lands in the わ row); Latin keys bucket
 * A–Z after diacritics are stripped; everything else (kanji with no reading, digits, symbols)
 * falls into ＃. Section order is kana rows first, then A–Z, then ＃.
 *
 * Which sections stand open is remembered across restarts, as the set of open section titles —
 * the same shape renrakusaki persists, so folding is a property of the section rather than of
 * this visit to the screen.
 */
object SkContacts {

    const val OTHER_SECTION = "#"
    const val FOLDED_INDICATOR = "▸"
    const val UNFOLDED_INDICATOR = "▾"

    /**
     * The sections standing OPEN, exactly as the sister address book stores them: a letter is shut
     * until it is opened, so both tabs start as a short index of letters rather than a long run of
     * names. The set is written to preferences on every toggle, so a fold outlives a restart.
     *
     * Scoped per tab — Contacts and Favorites are different lists, and folding B on one has no
     * business folding B on the other.
     */
    private const val KEY_EXPANDED_SECTIONS_PREFIX = "sk_expanded_contact_sections_"
    private const val KEY_SECTIONS_ENABLED = "sk_contact_sections"
    private const val KEY_USE_INITIALS = "sk_contact_initials"

    /** The two lists that carry letter headings; each remembers its own folds. */
    const val SCOPE_CONTACTS = "contacts"
    const val SCOPE_FAVOURITES = "favourites"

    /** Row leader → every hiragana character belonging to that row. */
    private val KANA_ROWS = listOf(
        'あ' to "ぁあぃいぅうぇえぉおゔ",
        'か' to "かがきぎくぐけげこごゕゖ",
        'さ' to "さざしじすずせぜそぞ",
        'た' to "ただちぢっつづてでとど",
        'な' to "なにぬねの",
        'は' to "はばぱひびぴふぶぷへべぺほぼぽ",
        'ま' to "まみむめも",
        'や' to "ゃやゅゆょよ",
        'ら' to "らりるれろ",
        'わ' to "ゎわゐゑをん",
    )

    private const val LATIN_SECTION_COUNT = 26

    /** One letter heading plus the run of contacts filed under it. */
    data class Section(
        val title: String,
        val count: Int,
        val expanded: Boolean,
        /** Full-width rules only frame open content, so a run of shut sections stays quiet. */
        val showTopDivider: Boolean,
    )

    // ------------------------------------------------------------------ settings

    /**
     * Whether a contact with no photo is drawn as its initials. Off by default: the house look —
     * both sister apps — puts the 人 mark there instead, which reads as "no photo" at a glance
     * where two letters read as content.
     */
    fun initialsEnabled(context: Context): Boolean =
        SkTheme.prefs(context).getBoolean(KEY_USE_INITIALS, false)

    fun setInitialsEnabled(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_USE_INITIALS, value).apply()
    }

    fun sectionsEnabled(context: Context): Boolean =
        SkTheme.prefs(context).getBoolean(KEY_SECTIONS_ENABLED, true)

    fun setSectionsEnabled(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_SECTIONS_ENABLED, value).apply()
    }

    private fun key(scope: String) = KEY_EXPANDED_SECTIONS_PREFIX + scope

    private fun expandedSections(context: Context, scope: String): Set<String> =
        SkTheme.prefs(context).getStringSet(key(scope), emptySet()).orEmpty()

    /** Whether a section stands open — none does, until it is opened. */
    fun isExpanded(context: Context, scope: String, title: String): Boolean =
        expandedSections(context, scope).contains(title)

    /** Flip one section open or shut, and remember it. Returns the new state. */
    fun toggleSection(context: Context, scope: String, title: String): Boolean {
        val expanded = expandedSections(context, scope).toMutableSet()
        val nowOpen = expanded.add(title)
        if (!nowOpen) {
            expanded.remove(title)
        }
        // Removed first: a StringSet handed back by SharedPreferences must never be edited in
        // place, and re-putting the same instance is exactly that.
        SkTheme.prefs(context).edit()
            .remove(key(scope))
            .putStringSet(key(scope), expanded)
            .apply()
        return nowOpen
    }

    // ------------------------------------------------------------------ bucketing

    /** The section title a sort key buckets under: a gojūon row leader, an A–Z letter, or ＃. */
    fun sectionTitleFor(sortKey: String?): String {
        val first = sortKey?.trim()?.firstOrNull() ?: return OTHER_SECTION
        kanaRowLeader(katakanaToHiragana(first))?.let { return it.toString() }
        val normalized = stripDiacritics(first.toString()).uppercase(Locale.ROOT).firstOrNull()
        return if (normalized != null && normalized in 'A'..'Z') normalized.toString() else OTHER_SECTION
    }

    /** Ordering rank of a section title: kana rows, then A–Z, then ＃. */
    fun sectionRank(title: String): Int {
        val c = title.firstOrNull() ?: return KANA_ROWS.size + LATIN_SECTION_COUNT
        val kanaIndex = KANA_ROWS.indexOfFirst { it.first == c }
        return when {
            kanaIndex >= 0 -> kanaIndex
            c in 'A'..'Z' -> KANA_ROWS.size + (c - 'A')
            else -> KANA_ROWS.size + LATIN_SECTION_COUNT
        }
    }

    private fun katakanaToHiragana(c: Char): Char = if (c in 'ァ'..'ヶ') c - 0x60 else c

    private fun kanaRowLeader(c: Char): Char? =
        KANA_ROWS.firstOrNull { (_, members) -> c in members }?.first

    /**
     * Diacritics folded away so "Č" files under C — [Normalizer] splits a letter from its marks,
     * and the marks are then dropped. (renrakusaki gets this from Fossify commons; upstream
     * Linphone has no equivalent, so it is done here directly.)
     */
    private fun stripDiacritics(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
}
