package com.eastmoney.agent.config;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * @Author: suyue
 * @name: McpConfig
 * @Date: 2026/09/11
 * @Description: 智谱联网搜索 MCP 客户端配置
 */
@Configuration
public class McpConfig {

    private static final String AUTHORIZATION = "Authorization";

    private static final Duration MCP_TIMEOUT = Duration.ofSeconds(60);

    @Value("${bigmodel.api-key}")
    private String apiKey;

    @Value("${bigmodel.mcp.web-search-url}")
    private String webSearchUrl;

    @Value("${bigmodel.mcp.log-enabled:false}")
    private Boolean logEnabled;

    /**
     * 创建智谱联网搜索 MCP 客户端
     */
    @Bean(destroyMethod = "close")
    public McpClient webSearchMcpClient() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("开启智谱 MCP 前请先配置 bigmodel.api-key");
        }

        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url(webSearchUrl)
                .customHeaders(Map.of(AUTHORIZATION, "Bearer " + apiKey))
                .timeout(MCP_TIMEOUT)
                .logRequests(Boolean.TRUE.equals(logEnabled))
                .logResponses(Boolean.TRUE.equals(logEnabled))
                .build();
        return DefaultMcpClient.builder()
                .key("bigmodelWebSearchMcpClient")
                .transport(transport)
                .build();
    }

    /**
     * 将 MCP 客户端提供的联网搜索能力转换为 Agent 工具
     */
    @Bean
    public McpToolProvider mcpToolProvider(McpClient webSearchMcpClient) {
        return McpToolProvider.builder()
                .mcpClients(webSearchMcpClient)
                .build();
    }
}
