package com.eastmoney.agent.config;

import com.eastmoney.agent.repository.SqliteChatMemoryStore;
import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.tool.InterviewQuestionTool;
import com.eastmoney.agent.tool.KnowledgeSearchTool;
import com.eastmoney.agent.transformer.AgentChatRequestTransformer;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    private static final Map<String, Object> THINKING_DISABLED_PARAMETERS =
            Map.of("thinking", Map.of("type", "disabled"));

    @Value("${ai.chat.base_url}")
    private String chatUrl;

    @Value("${ai.chat.api-key}")
    private String chatApiKey;

    @Value("${ai.chat.model}")
    private String chatModelName;

    @Value("${ai.chat.thinking-enabled}")
    private Boolean thinkingEnabled;

    @Value("${agent.max-steps}")
    private Integer maxSteps;

    @Value("${ai.chat-memory.max-messages}")
    private Integer maxMemoryMessages;

    @Resource
    private ChatModelListener chatModelListener;

    /**
     * 创建基于 OpenAI Chat Completions 兼容协议的聊天模型客户端
     */
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .baseUrl(resolveBaseUrl(chatUrl, CHAT_COMPLETIONS_PATH))
                .apiKey(chatApiKey)
                .modelName(chatModelName)
                .listeners(List.of(chatModelListener))
                .temperature(0.3D)
                .customParameters(chatCustomParameters())
                .timeout(CHAT_TIMEOUT)
                .build();
    }

    /**
     * 创建基于 OpenAI Chat Completions 兼容协议的流式聊天模型客户端
     */
    @Bean
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(resolveBaseUrl(chatUrl, CHAT_COMPLETIONS_PATH))
                .apiKey(chatApiKey)
                .modelName(chatModelName)
                .listeners(List.of(chatModelListener))
                .temperature(0.3D)
                .customParameters(chatCustomParameters())
                .timeout(CHAT_TIMEOUT)
                .build();
    }

    @Bean
    public AgentAssistant agentAssistant(ChatModel chatModel,
                                         StreamingChatModel streamingChatModel,
                                         KnowledgeSearchTool knowledgeSearchTool,
                                         InterviewQuestionTool interviewQuestionTool,
                                         ObjectProvider<McpToolProvider> mcpToolProvider,
                                         AgentChatRequestTransformer agentChatRequestTransformer,
                                         SqliteChatMemoryStore chatMemoryStore) {
        AiServices<AgentAssistant> agentAssistantBuilder = AiServices.builder(AgentAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.builder()
                                .id(memoryId)
                                .maxMessages(maxMemoryMessages)
                                .chatMemoryStore(chatMemoryStore)
                                .build()) // 每个会话独立存储，并持久化到 SQLite
                .tools(knowledgeSearchTool, interviewQuestionTool) //工具调用
                .chatRequestTransformer(agentChatRequestTransformer::transform) // 按命令或问题选择工具
                .maxToolCallingRoundTrips(maxSteps);
        mcpToolProvider.ifAvailable(agentAssistantBuilder::toolProvider);
        return agentAssistantBuilder.build();
    }

    private String resolveBaseUrl(String endpoint, String apiPath) {
        String baseUrl = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (baseUrl.endsWith(apiPath)) {
            return baseUrl.substring(0, baseUrl.length() - apiPath.length());
        }
        return baseUrl;
    }

    private Map<String, Object> chatCustomParameters() {
        return Boolean.TRUE.equals(thinkingEnabled) ? Map.of() : THINKING_DISABLED_PARAMETERS;
    }
}
