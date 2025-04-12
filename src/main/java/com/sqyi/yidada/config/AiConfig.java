package com.sqyi.yidada.config;

import com.volcengine.ark.runtime.service.ArkService;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * @author sqyi
 *
 */
@Configuration
@ConfigurationProperties(prefix = "ai.service-config")
@Data
public class AiConfig {
    private String apiKeyName;
    private String baseUrl;
    private Integer maxIdleConnections;
    private Integer keepAliveDuration;

    @Bean
    public ArkService getArkService() {
        // 从环境变量中获取 API Key
        String apiKey = System.getenv(apiKeyName);
        // OkHttp 连接池配置（最大空闲连接数5个，保持时间1秒）
        ConnectionPool connectionPool =
                new ConnectionPool(maxIdleConnections, keepAliveDuration, TimeUnit.SECONDS);
        // 网络请求调度器
        Dispatcher dispatcher = new Dispatcher();
        // 构建 Ark 服务客户端实例
        return ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
    }
}
