package com.sandeepvedavyas.tripagent.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ParticipantHintTest {
    @Test
    fun `days and an uncounted kids mention are not a party size`() {
        assertThat(ParticipantHint.infer("3 days in Lisbon with kids, mid budget")).isNull()
    }

    @Test
    fun `adults and kids are added`() {
        assertThat(ParticipantHint.infer("2 adults and 2 kids in Rome")).isEqualTo(4)
    }

    @Test
    fun `party of wins`() {
        assertThat(ParticipantHint.infer("party of 5 for a weekend")).isEqualTo(5)
    }

    @Test
    fun `kids alone still count one adult`() {
        assertThat(ParticipantHint.infer("a museum day for 3 kids")).isEqualTo(4)
    }
}
