package com.sandeepvedavyas.tripagent

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TripAgentApplication

fun main(args: Array<String>) {
    runApplication<TripAgentApplication>(*args)
}
