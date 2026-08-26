package com.bookdecision.application.userdataset;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(UserDatasetProperties.class)
public class UserDatasetConfiguration {

    @Bean
    Clock userDatasetClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
    MinioClient userDatasetMinioClient(UserDatasetProperties properties) {
        UserDatasetProperties.Minio minio = properties.minio();
        requireText(minio.endpoint(), "MINIO_ENDPOINT");
        requireText(minio.accessKey(), "MINIO_ACCESS_KEY");
        requireText(minio.secretKey(), "MINIO_SECRET_KEY");
        requireText(minio.bucket(), "MINIO_BUCKET");
        return MinioClient.builder()
                .endpoint(minio.endpoint())
                .credentials(minio.accessKey(), minio.secretKey())
                .build();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured when private user datasets are enabled");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "book-decision.user-dataset", name = "enabled", havingValue = "true")
    @EnableScheduling
    static class SchedulingConfiguration {
    }
}
