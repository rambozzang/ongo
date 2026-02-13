package com.ongo.infrastructure.external.storage

import io.minio.MinioClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@ConfigurationProperties(prefix = "storage")
data class StorageProperties(
    val type: String = "minio",
    val bucket: String = "ongo-videos",
    val minio: MinioProperties = MinioProperties(),
    val s3: S3Properties = S3Properties(),
) {
    data class MinioProperties(
        val endpoint: String = "http://localhost:9000",
        val accessKey: String = "minioadmin",
        val secretKey: String = "minioadmin",
    )

    data class S3Properties(
        val region: String = "ap-northeast-2",
    )
}

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    @Bean
    @Profile("local", "default")
    fun minioClient(storageProperties: StorageProperties): MinioClient {
        return MinioClient.builder()
            .endpoint(storageProperties.minio.endpoint)
            .credentials(storageProperties.minio.accessKey, storageProperties.minio.secretKey)
            .build()
    }

    @Bean
    @Profile("production")
    fun s3Client(storageProperties: StorageProperties): S3Client {
        return S3Client.builder()
            .region(Region.of(storageProperties.s3.region))
            .build()
    }

    @Bean
    @Profile("production")
    fun s3Presigner(storageProperties: StorageProperties): S3Presigner {
        return S3Presigner.builder()
            .region(Region.of(storageProperties.s3.region))
            .build()
    }
}
