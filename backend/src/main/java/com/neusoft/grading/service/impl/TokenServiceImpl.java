package com.neusoft.grading.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.grading.dto.UserInfoResponse;
import com.neusoft.grading.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Token / Redis 会话管理
 *
 * Redis 会话仅存储身份标识三要素（role / userNo / name），
 * 完整用户信息由 AuthServiceImpl 从 MySQL 补全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    /** Redis Key 前缀，对应技术设计文档 session:user:{token_hash} */
    public static final String TOKEN_PREFIX = "neusoft:session:user:";

    private static final long TOKEN_EXPIRE_HOURS = 2;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String createToken(String role, String userNo, String name) {
        String token = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("role", role);
        sessionData.put("userNo", userNo);
        sessionData.put("name", name);

        try {
            String json = objectMapper.writeValueAsString(sessionData);
            stringRedisTemplate.opsForValue().set(
                    TOKEN_PREFIX + token, json, TOKEN_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("Token 已创建: tokenPrefix={}, role={}, userNo={}",
                    TOKEN_PREFIX + token.substring(0, 8) + "...", role, userNo);
        } catch (Exception e) {
            log.error("Token 序列化失败", e);
            throw new RuntimeException("Token 创建失败", e);
        }

        return token;
    }

    @Override
    public UserInfoResponse getUserInfo(String token) {
        String json = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (json == null) {
            return null;
        }

        try {
            Map<String, String> data = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
            // Redis 会话仅存 role / userNo / name，其余字段由 AuthServiceImpl 从 DB 补全
            return UserInfoResponse.builder()
                    .role(data.get("role"))
                    .userNo(data.get("userNo"))
                    .name(data.get("name"))
                    .build();
        } catch (Exception e) {
            log.error("Token 反序列化失败: tokenPrefix={}",
                    TOKEN_PREFIX + (token.length() > 8 ? token.substring(0, 8) : token) + "...", e);
            return null;
        }
    }

    @Override
    public String getRole(String token) {
        UserInfoResponse info = getUserInfo(token);
        return info != null ? info.getRole() : null;
    }

    @Override
    public String getUserNo(String token) {
        UserInfoResponse info = getUserInfo(token);
        return info != null ? info.getUserNo() : null;
    }

    @Override
    public void removeToken(String token) {
        stringRedisTemplate.delete(TOKEN_PREFIX + token);
    }
}
