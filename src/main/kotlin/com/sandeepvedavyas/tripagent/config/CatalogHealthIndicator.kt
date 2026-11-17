package com.sandeepvedavyas.tripagent.config

import com.sandeepvedavyas.tripagent.catalog.ActivityCatalog
import com.sandeepvedavyas.tripagent.domain.DemoNotice
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component("catalog")
class CatalogHealthIndicator(
    private val catalog: ActivityCatalog,
) : HealthIndicator {
    override fun health(): Health = Health.up()
        .withDetail("activities", catalog.size)
        .withDetail("cities", catalog.cities.sorted())
        .withDetail("disclaimer", DemoNotice.TEXT)
        .build()
}
