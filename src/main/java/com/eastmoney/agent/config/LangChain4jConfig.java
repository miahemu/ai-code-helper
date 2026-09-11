package com.eastmoney.agent.config;

import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.tool.InterviewQuestionTool;
import com.eastmoney.agent.tool.KnowledgeSearchTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
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

    @Value("${ai.chat.base_url}")
    private String chatUrl;

    @Value("${ai.chat.api-key}")
    private String chatApiKey;

    @Value("${ai.chat.model}")
    private String chatModelName;

    @Value("${http.read-timeout}")
    private Integer readTimeout;

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
                .timeout(Duration.ofMillis(readTimeout))
                .build();
    }

    /**
     * 会话记忆（默认 20 条上下文）
     * @return
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(Math.max(1, maxMemoryMessages))
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }

    @Bean
    public AgentAssistant agentAssistant(ChatModel chatModel,
                                         ChatMemoryProvider chatMemoryProvider,
                                         KnowledgeSearchTool knowledgeSearchTool,
                                         InterviewQuestionTool interviewQuestionTool) {
        return AiServices.builder(AgentAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(knowledgeSearchTool, interviewQuestionTool)
                .maxToolCallingRoundTrips(Math.max(1, maxSteps))
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
