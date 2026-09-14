package com.eastmoney.agent.transformer;

import com.eastmoney.agent.enums.ChatCommandEnum;
import com.eastmoney.agent.tool.AgentToolConstants;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @Author: suyue
 * @name: AgentChatRequestTransformer
 * @Date: 2026/09/14
 * @Description: Agent 命令工具路由请求转换器
 */
@Component
public class AgentChatRequestTransformer {

    @Value("${bigmodel.enabled}")
    private Boolean webSearchAvailable;

    /**
     * 根据显式命令或自动规则选择工具
     *
     * @param request 模型对答请求
     * @return 转换后的模型对答请求
     */
    public ChatRequest transform(ChatRequest request) {
        UserMessage userMessage = UserMessage.findLast(request.messages()).orElse(null);
        if (userMessage == null || !userMessage.hasSingleText()) {
            return request;
        }

        return routeRequest(request, userMessage.singleText());
    }


    /**
     * 根据显式命令或联网规则决定工具调用方式
     */
    private ChatRequest routeRequest(ChatRequest request, String question) {
        ChatCommandEnum command = ChatCommandEnum.fromQuestion(question);

        // 未知命令不做强制路由，交给上层的命令校验或模型自行处理
        if (command == ChatCommandEnum.UNKNOWN) {
            return request;
        }

        // /kb 是知识库专用模式，只允许模型调用知识库搜索工具
        if (command == ChatCommandEnum.KNOWLEDGE) {
            return requireTool(request, tool -> AgentToolConstants.KNOWLEDGE_SEARCH.equals(tool.name()),
                    "未找到知识库搜索工具");
        }

        // MCP 工具名称可能变化，因此通过 McpConfig 写入的元数据识别联网工具
        Predicate<ToolSpecification> webSearchToolMatcher = tool -> {
            Map<String, Object> metadata = tool.metadata();
            return metadata != null && Boolean.TRUE.equals(
                    metadata.get(AgentToolConstants.WEB_SEARCH_METADATA_KEY));
        };
        // /interview 是面试题专用模式，只允许调用面试题搜索工具
        if (command == ChatCommandEnum.INTERVIEW) {
            return requireTool(request, tool -> AgentToolConstants.INTERVIEW_SEARCH.equals(tool.name()),
                    "未找到面试题搜索工具");
        }

        // 普通问题涉及最新、实时等信息时，强制联网，避免模型仅凭记忆回答
        if (Boolean.TRUE.equals(webSearchAvailable) && WebSearchPolicy.requiresWebSearch(question)) {
            return requireTool(request, webSearchToolMatcher, "未找到联网搜索工具");
        }

        // 普通问题或 /auto 不满足时效条件时，保留全部工具交给模型自主选择
        return request.toBuilder().toolChoice(ToolChoice.AUTO).build();
    }


    /**
     * 筛选并强制调用指定类型的工具，工具已执行时关闭本轮后续工具调用
     */
    private ChatRequest requireTool(ChatRequest request, Predicate<ToolSpecification> toolMatcher, String missingMessage) {
        List<ToolSpecification> toolSpecifications = request.toolSpecifications().stream()
                .filter(toolMatcher)
                .toList();
        if (toolSpecifications.isEmpty()) {
            throw new IllegalStateException(missingMessage);
        }
        Set<String> toolNames = toolSpecifications.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        if (hasToolResult(request, toolNames)) {
            return request.toBuilder().toolChoice(ToolChoice.NONE).build();
        }
        return request.toBuilder()
                .toolSpecifications(toolSpecifications)
                .toolChoice(ToolChoice.REQUIRED)
                .build();
    }


    private boolean hasToolResult(ChatRequest request, Set<String> toolNames) {
        List<ChatMessage> messages = request.messages();
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message instanceof ToolExecutionResultMessage result
                    && toolNames.contains(result.toolName())) {
                return true;
            }
            if (message instanceof UserMessage) {
                return false;
            }
        }
        return false;
    }
}
