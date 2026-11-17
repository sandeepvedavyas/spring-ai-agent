package com.sandeepvedavyas.tripagent.catalog

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ActivityCatalogTest {
    private val catalog = ActivityCatalog(jacksonObjectMapper())

    @Test
    fun `seed covers four cities and at least thirty activities`() {
        assertThat(catalog.size).isGreaterThanOrEqualTo(30)
        assertThat(catalog.cities).containsExactlyInAnyOrder("Lisbon", "Barcelona", "Berlin", "Rome")
        assertThat(catalog.disclaimer).contains("GetYourGuide").contains("not scraped")
    }

    @Test
    fun `search filters city category price and sold out dates`() {
        val family = catalog.search("lisboa", "kids", 40.0, "2026-06-12")
        assertThat(family.error).isNull()
        assertThat(family.city).isEqualTo("Lisbon")
        assertThat(family.activities).isNotEmpty
        assertThat(family.activities).allMatch { it.city == "Lisbon" && it.pricePerPerson <= BigDecimal("40.00") }
        assertThat(family.activities.map { it.activityId }).contains("lis-oceanario")

        val soldOut = catalog.search("Lisbon", "sightseeing", 0.0, "2026-06-13")
        assertThat(soldOut.activities.map { it.activityId }).doesNotContain("lis-belem-tower")
        assertThat(soldOut.note).contains("sold out")

        val unknown = catalog.search("Paris", "any", 0.0, "")
        assertThat(unknown.error).contains("Unknown city")
        assertThat(unknown.activities).isEmpty()
    }

    @Test
    fun `price applies the group rate at four participants`() {
        val pair = catalog.price("lis-oceanario", 2)
        assertThat(pair.error).isNull()
        assertThat(pair.pricePerPerson).isEqualByComparingTo("28.00")
        assertThat(pair.total).isEqualByComparingTo("56.00")
        assertThat(pair.groupDiscountApplied).isFalse()

        val group = catalog.price("lis-oceanario", 4)
        assertThat(group.pricePerPerson).isEqualByComparingTo("25.20")
        assertThat(group.total).isEqualByComparingTo("100.80")
        assertThat(group.groupDiscountApplied).isTrue()

        assertThat(catalog.price("missing", 2).error).contains("Unknown activityId")
        assertThat(catalog.price("lis-kayak", 9).error).contains("at most")
    }

    @Test
    fun `availability reports a closed date`() {
        val closed = catalog.availability("lis-belem-tower", "2026-06-13", 2)
        assertThat(closed.available).isFalse()
        assertThat(closed.remaining).isZero()
        assertThat(closed.reason).contains("Sold out")

        val open = catalog.availability("lis-belem-tower", "2026-06-12", 2)
        assertThat(open.available).isTrue()
        assertThat(open.remaining).isEqualTo(20)

        assertThatThrownBy { catalog.availability("lis-belem-tower", "tomorrow", 2) }
            .isInstanceOf(BadCatalogInput::class.java)
    }

    @Test
    fun `reviews answer skip the line kids and rain`() {
        val indoor = catalog.summarizeReviews("lis-oceanario")
        assertThat(indoor.skipTheLine).contains("Skip-the-line: included")
        assertThat(indoor.kidFriendly).contains("Kid friendly: yes")
        assertThat(indoor.rain).contains("Rain: covered")
        assertThat(indoor.excerpts).isNotEmpty

        val outdoor = catalog.summarizeReviews("lis-kayak")
        assertThat(outdoor.skipTheLine).contains("not included")
        assertThat(outdoor.rain).contains("outdoors")

        val night = catalog.summarizeReviews("lis-fado-night")
        assertThat(night.kidFriendly).contains("Kid friendly: no")
    }

    @Test
    fun `cross sell stays in the same city and skips the booked activity`() {
        val result = catalog.crossSell("lis-belem-tower")
        assertThat(result.error).isNull()
        assertThat(result.suggestions).isNotEmpty
        assertThat(result.suggestions.map { it.activityId }).doesNotContain("lis-belem-tower")
        assertThat(result.suggestions).allMatch { it.city == "Lisbon" }
        assertThat(result.suggestions.map { it.activityId }).contains("lis-pastel-workshop")
        assertThat(catalog.crossSell("nope").error).contains("Unknown activityId")
    }
}
