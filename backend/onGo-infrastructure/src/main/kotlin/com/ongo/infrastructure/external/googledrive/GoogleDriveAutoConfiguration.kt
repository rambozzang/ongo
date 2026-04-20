package com.ongo.infrastructure.external.googledrive

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GoogleDriveProperties::class)
class GoogleDriveAutoConfiguration
