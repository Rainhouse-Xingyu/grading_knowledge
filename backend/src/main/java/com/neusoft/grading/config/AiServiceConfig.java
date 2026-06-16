package com.neusoft.grading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 算法服务配置
 *
 * 从 application.yml 读取 FastAPI 服务地址和路径配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai-service")
public class AiServiceConfig {

    /** FastAPI 服务基础地址 */
    private String baseUrl = "http://localhost:8000";

    /** 评测任务提交路径 */
    private String submitPath = "/ai/eval/submit";

    /** 健康检查路径 */
    private String healthPath = "/ai/health";

    /** 每日 DeepSeek 调用配额上限 */
    private int dailyQuota = 500;

    /** 获取完整的提交 URL */
    public String getSubmitUrl() {
        return baseUrl + submitPath;
    }

    /** 获取完整的健康检查 URL */
    public String getHealthUrl() {
        return baseUrl + healthPath;
    }
}
