package com.neusoft.grading.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 *
 * 用于 Spring Boot 主服务向 Python FastAPI 算法服务发送 HTTP 请求。
 * 连接超时 5s，读取超时 300s（AI 评测可能耗时较长）。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(300000);
        return new RestTemplate(factory);
    }
}
