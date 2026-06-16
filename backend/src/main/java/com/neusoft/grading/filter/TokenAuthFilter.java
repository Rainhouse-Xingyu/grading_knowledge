package com.neusoft.grading.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.UserInfoResponse;
import com.neusoft.grading.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Token 认证过滤器：拦截请求，从 Authorization 头提取 Token，查询 Redis 会话，
 * 将用户信息写入 Spring Security 上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(TOKEN_PREFIX)) {
            String token = header.substring(TOKEN_PREFIX.length());

            UserInfoResponse userInfo = tokenService.getUserInfo(token);
            if (userInfo != null) {
                // 将用户角色写入 Spring Security 上下文
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + userInfo.getRole());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userInfo.getUserNo(), null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 将用户信息放入 request attribute，方便 Controller 使用
                request.setAttribute("currentUser", userInfo);
            }
        }

        filterChain.doFilter(request, response);
    }
}
