package org.linphone.shiroikuma

/**
 * shiroikuma-rindenwa fork — how a phone number is written, wherever one shows.
 *
 * The house reading groups a number by its country: Czech as `+420-601-123-456`, North American
 * as `+1-808-555-0123`. A provider rarely hands the number over that way. Odorik delivers a Czech
 * caller as nine bare digits — `601123456` — and names him `601123456>456`, the number followed by
 * the tail of the line it rang; neither is how 白い熊 reads a number, and the second is not a
 * name at all.
 */
object SkNumbers {
    /**
     * The country a number written without one belongs to. Every line 白い熊 has is Czech, and a
     * Czech national number is exactly [NATIONAL_DIGITS] long, which is what keeps a short code or
     * an internal extension from being dressed up as one.
     */
    private const val HOME_PREFIX = "420"
    private const val NATIONAL_DIGITS = 9

    /**
     * Country code → how its national number is grouped, most specific first. A candidate only
     * applies when the digits after the code match the grouping's total length exactly, which is
     * what keeps "1" from swallowing unrelated numbers.
     */
    private val GROUPINGS = listOf(
        "420" to listOf(3, 3, 3),
        "1" to listOf(3, 3, 4),
    )

    /**
     * [raw] in the house reading, or exactly as it arrived when it is not a number of a country
     * we group: an extension, a short code, a SIP user name.
     */
    fun format(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.isEmpty() || trimmed.any { it.isLetter() }) return raw

        val international = when {
            trimmed.startsWith("+") -> digits
            trimmed.startsWith("00") -> digits.removePrefix("00")
            digits.length == NATIONAL_DIGITS -> HOME_PREFIX + digits
            else -> digits
        }

        for ((countryCode, groups) in GROUPINGS) {
            if (!international.startsWith(countryCode)) continue
            val national = international.removePrefix(countryCode)
            if (national.length != groups.sum()) continue

            val parts = ArrayList<String>(groups.size + 1)
            parts.add("+$countryCode")
            var offset = 0
            for (group in groups) {
                parts.add(national.substring(offset, offset + group))
                offset += group
            }
            return parts.joinToString("-")
        }
        return raw
    }

    /**
     * What to call a caller who is in no address book. A display name that is only the number over
     * again — bare, or with Odorik's `>456` tail — is replaced by the number in the house reading;
     * a real name the other end sent is kept.
     */
    fun unknownCallerName(displayName: String?, username: String?): String {
        val name = displayName.orEmpty().trim()
        val head = name.substringBefore('>').substringBefore('›').trim()
        val isNumber = head.isNotEmpty() && head.all { it.isDigit() || it in "+ -()" } && head.any(Char::isDigit)
        return when {
            isNumber -> format(head)
            name.isEmpty() -> format(username.orEmpty())
            else -> name
        }
    }
}
