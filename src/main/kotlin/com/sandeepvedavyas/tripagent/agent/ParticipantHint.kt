package com.sandeepvedavyas.tripagent.agent

object ParticipantHint {
    private val adults = Regex("""(\d+)\s*(adults?|people|travelers?|travellers?|guests?|participants?)""", RegexOption.IGNORE_CASE)
    private val children = Regex("""(\d+)\s*(kids?|children|child)""", RegexOption.IGNORE_CASE)
    private val party = Regex("""party of\s*(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * Reads an explicit party size. "3 days" and "with kids" do not count.
     * "2 adults and 2 kids" sums both. A kids-only count adds one adult.
     */
    fun infer(message: String): Int? {
        val adultCount = adults.find(message)?.groupValues?.get(1)?.toIntOrNull()
        val childCount = children.find(message)?.groupValues?.get(1)?.toIntOrNull()
        val partyCount = party.find(message)?.groupValues?.get(1)?.toIntOrNull()
        val raw = when {
            adultCount != null && childCount != null -> adultCount + childCount
            partyCount != null -> partyCount
            adultCount != null -> adultCount
            childCount != null -> childCount + 1
            else -> return null
        }
        return raw.coerceIn(1, 15)
    }
}
