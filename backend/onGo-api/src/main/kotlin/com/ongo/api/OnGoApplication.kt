package com.ongo.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.ongo"])
@EnableCaching
@EnableAsync
@EnableScheduling
class OnGoApplication

fun main(args: Array<String>) {
    runApplication<OnGoApplication>(*args)
}
