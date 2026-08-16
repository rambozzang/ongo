package com.ongo.api.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Enables durable workers only for a running application. Test contexts use a
 * disposable database and must not let a scheduled tick race its shutdown.
 */
@Configuration
@Profile("!test")
@ConditionalOnProperty(
    name = ["ongo.scheduling.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@EnableScheduling
class SchedulingConfiguration
