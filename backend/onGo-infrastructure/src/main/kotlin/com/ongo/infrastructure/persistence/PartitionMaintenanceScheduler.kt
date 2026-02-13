package com.ongo.infrastructure.persistence

import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class PartitionMaintenanceScheduler(
    private val dsl: DSLContext,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 2 1 * *") // 1st of every month at 2 AM
    fun createUpcomingPartitions() {
        log.info("Running analytics partition maintenance")
        try {
            dsl.execute("SELECT fn_create_analytics_partitions(3)")
            log.info("Analytics partition maintenance completed")
        } catch (e: Exception) {
            log.error("Failed to create analytics partitions: ${e.message}", e)
        }
    }
}
