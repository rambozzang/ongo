package com.ongo.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import java.util.concurrent.Executor

/**
 * Virtual Thread 기반 비동기 처리 설정
 *
 * Spring @Async가 사용하는 기본 Executor를 Virtual Thread 기반으로 설정합니다.
 * Virtual Thread는 JDK 21+에서 가볍게 생성/소멸되지만,
 * VirtualThreadTaskExecutor로 래핑하여 Spring이 스레드 풀 메트릭을 추적할 수 있게 합니다.
 */
@Configuration
class AsyncConfig : AsyncConfigurer {

    @Bean("taskExecutor")
    fun virtualThreadTaskExecutor(): TaskExecutor =
        VirtualThreadTaskExecutor("ongo-async-")

    override fun getAsyncExecutor(): Executor = virtualThreadTaskExecutor()
}
