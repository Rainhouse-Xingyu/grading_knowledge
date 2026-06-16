package com.neusoft.grading.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储客户端配置
 *
 * 从 application.yml 读取 minio.* 配置项，
 * 创建 MinioClient Bean 并在启动时自动检测/创建 Bucket。
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    /** MinIO 服务端点，例如 http://localhost:9000 */
    private String endpoint;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** 存储桶名称 */
    private String bucketName;

    /**
     * 创建 MinIO 客户端 Bean
     */
    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        // 启动时自动检测 Bucket 是否存在，不存在则创建
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO Bucket 已创建: {}", bucketName);
            } else {
                log.debug("MinIO Bucket 已存在: {}", bucketName);
            }
        } catch (Exception e) {
            log.warn("MinIO Bucket 检测/创建失败（MinIO 服务可能未启动）: {}", e.getMessage());
        }

        return client;
    }
}
