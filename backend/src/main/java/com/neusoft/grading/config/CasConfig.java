package com.neusoft.grading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CAS 统一身份认证配置
 *
 * application.yml 示例：
 * cas:
 *   server-url: https://cas.neusoft.edu.cn
 *   service-name: http://localhost:8080
 *   login-url: https://cas.neusoft.edu.cn/login?service=http://localhost:8080
 *   logout-url: https://cas.neusoft.edu.cn/logout?service=http://localhost:8080
 *   mock: true
 */
@Data
@Component
@ConfigurationProperties(prefix = "cas")
public class CasConfig {

    /** CAS 服务器地址，例如 https://cas.neusoft.edu.cn */
    private String serverUrl;

    /** 本系统在 CAS 注册的服务名称（回调 URL） */
    private String serviceName;

    /** CAS 登录页面地址（用于登出重定向） */
    private String loginUrl;

    /** CAS 登出地址 */
    private String logoutUrl;

    /**
     * 开发模拟开关：true 时不走真实 CAS 验证，通过 ticket 前缀模拟角色。
     * 生产环境务必设为 false 或删除此配置项。
     */
    private boolean mock = true;
}
