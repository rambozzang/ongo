package com.ongo.infrastructure.payment

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(PaddleConfig::class)
class PaddleAutoConfiguration
