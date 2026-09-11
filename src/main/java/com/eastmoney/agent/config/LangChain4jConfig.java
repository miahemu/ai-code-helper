package com.eastmoney.agent.config;

import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.tool.InterviewQuestionTool;
import com.eastmoney.agent.tool.KnowledgeSearchTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * @Author: suyue
 * @name: LangChain4jConfig
 * @Date: 2026/09/10
 * @Description: LangChain4j 模型和 AI Service 配置
 */
@Configuration
public class LangChain4jConfig {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final Duration CHAT_TIMEOUT = Duration.ofSeconds(60);

    @Value("${ai.chat.base_url}")
    private String chatUrl;

    @Value("${ai.chat.api-key}")
    private String chatApiKey;

    @Value("${ai.chat.model}")
    private String chatModelName;

    @Value("${agent.max-steps}")
    private Integer maxSteps;

    @Value("${ai.chat-memory.max-messages}")
    private Integer maxMemoryMessages;

    /**
     * 创建基于 OpenAI Chat Completions 兼容协议的聊天模型客户端
     */
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(resolveBaseUrl(chatUrl, CHAT_COMPLETIONS_PATH))
                .apiKey(chatApiKey)
                .modelName(chatModelName)
                .temperature(0.3D)
                .timeout(CHAT_TIMEOUT)
                .build();
    }

    @Bean
    public AgentAssistant agentAssistant(ChatModel chatModel,
                                         KnowledgeSearchTool knowledgeSearchTool,
                                         InterviewQuestionTool interviewQuestionTool,
                                         McpToolProvider mcpToolProvider) {
        return AiServices.builder(AgentAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(maxMemoryMessages)) // 每个会话独立存储
                .tools(knowledgeSearchTool, interviewQuestionTool) //工具调用
                .toolProvider(mcpToolProvider) // MCP 工具调用
                .maxToolCallingRoundTrips(maxSteps)
                .build();
    }

    private String resolveBaseUrl(String endpoint, String apiPath) {
        String baseUrl = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (baseUrl.endsWith(apiPath)) {
            return baseUrl.substring(0, baseUrl.length() - apiPath.length());
        }
        return baseUrl;
    }
}
