package com.ongo.infrastructure.external.storage

import io.minio.MinioClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

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
        val endpoint: String? = null,
        val accessKey: String? = null,
        val secretKey: String? = null,
    )
}

@Configuration
@EnableConfigurationProperties(StorageProperties::class)
class StorageConfig {

    @Bean
    @Profile("local", "default", "dev")
    fun minioClient(storageProperties: StorageProperties): MinioClient {
        return MinioClient.builder()
            .endpoint(storageProperties.minio.endpoint)
            .credentials(storageProperties.minio.accessKey, storageProperties.minio.secretKey)
            .build()
    }

    @Bean
    @Profile("production")
    fun s3Client(storageProperties: StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(storageProperties.s3.region))

        val s3Props = storageProperties.s3
        if (!s3Props.endpoint.isNullOrBlank()) {
            builder.endpointOverride(URI(s3Props.endpoint))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build(),
                )
        }

        if (!s3Props.accessKey.isNullOrBlank() && !s3Props.secretKey.isNullOrBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Props.accessKey, s3Props.secretKey),
                ),
            )
        }

        return builder.build()
    }

    @Bean
    @Profile("production")
    fun s3Presigner(storageProperties: StorageProperties): S3Presigner {
        val builder = S3Presigner.builder()
            .region(Region.of(storageProperties.s3.region))

        val s3Props = storageProperties.s3
        if (!s3Props.endpoint.isNullOrBlank()) {
            builder.endpointOverride(URI(s3Props.endpoint))
                .serviceConfiguration(
                    S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build(),
                )
        }

        if (!s3Props.accessKey.isNullOrBlank() && !s3Props.secretKey.isNullOrBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Props.accessKey, s3Props.secretKey),
                ),
            )
        }

        return builder.build()
    }
}
