package com.eastmoney.agent.config;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author: suyue
 * @name: WebSearchRequestTransformer
 * @Date: 2026/09/11
 * @Description: 知识库优先及联网搜索工具调用请求转换器
 */
public final class WebSearchRequestTransformer {

    static final String WEB_SEARCH_TOOL_METADATA_KEY = "webSearchTool";

    private static final String KNOWLEDGE_SEARCH_TOOL_NAME = "knowledge_search";

    private static final Pattern REQUIRED_PATTERN = Pattern.compile(
            "(最新|今日|今天|实时|联网(?:搜索|查询|查找)?|网络搜索|网上搜索|"
                    + "近期(?:新闻|动态|消息)|本周(?:新闻|动态|消息)|本月(?:新闻|动态|消息)|"
                    + "latest|today|real[- ]?time|search (?:the )?web|online search)",
            Pattern.CASE_INSENSITIVE);

    private WebSearchRequestTransformer() {
    }

    /**
     * 每个新问题先检索知识库，知识库返回后再按需强制联网搜索
     */
    public static ChatRequest transform(ChatRequest request) {
        UserMessage userMessage = UserMessage.findLast(request.messages()).orElse(null);
        if (userMessage == null || !userMessage.hasSingleText()) {
            return request;
        }

        List<ToolSpecification> knowledgeSearchTools = request.toolSpecifications().stream()
                .filter(toolSpecification -> KNOWLEDGE_SEARCH_TOOL_NAME.equals(toolSpecification.name()))
                .toList();
        if (!knowledgeSearchTools.isEmpty()
                && !hasToolResult(request.messages(), Set.of(KNOWLEDGE_SEARCH_TOOL_NAME))) {
            return requireTool(request, knowledgeSearchTools);
        }

        if (!requiresWebSearch(userMessage.singleText())) {
            return request;
        }

        List<ToolSpecification> webSearchTools = request.toolSpecifications().stream()
                .filter(WebSearchRequestTransformer::isWebSearchTool)
                .toList();
        if (webSearchTools.isEmpty()) {
            throw new IllegalStateException("未找到联网搜索工具");
        }

        Set<String> webSearchToolNames = webSearchTools.stream()
                .map(ToolSpecification::name)
                .collect(Collectors.toSet());
        if (hasToolResult(request.messages(), webSearchToolNames)) {
            return request;
        }
        return requireTool(request, webSearchTools);
    }

    private static ChatRequest requireTool(ChatRequest request, List<ToolSpecification> toolSpecifications) {
        return request.toBuilder()
                .toolSpecifications(toolSpecifications)
                .toolChoice(ToolChoice.REQUIRED)
                .build();
    }

    public static boolean requiresWebSearch(String question) {
        return question != null && REQUIRED_PATTERN.matcher(question).find();
    }

    private static boolean isWebSearchTool(ToolSpecification toolSpecification) {
        Map<String, Object> metadata = toolSpecification.metadata();
        return metadata != null && Boolean.TRUE.equals(metadata.get(WEB_SEARCH_TOOL_METADATA_KEY));
    }

    private static boolean hasToolResult(List<ChatMessage> messages, Set<String> toolNames) {
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
