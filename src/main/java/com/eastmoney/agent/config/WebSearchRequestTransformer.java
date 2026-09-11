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
 * @Description: 时效性问题联网搜索请求转换器
 */
public final class WebSearchRequestTransformer {

    static final String WEB_SEARCH_TOOL_METADATA_KEY = "webSearchTool";

    private static final Pattern REQUIRED_PATTERN = Pattern.compile(
            "(最新|今日|今天|实时|联网(?:搜索|查询|查找)?|网络搜索|网上搜索|"
                    + "近期(?:新闻|动态|消息)|本周(?:新闻|动态|消息)|本月(?:新闻|动态|消息)|"
                    + "latest|today|real[- ]?time|search (?:the )?web|online search)",
            Pattern.CASE_INSENSITIVE);

    private WebSearchRequestTransformer() {
    }

    /**
     * 时效性问题首轮只允许调用联网搜索，工具返回后恢复正常处理
     */
    public static ChatRequest transform(ChatRequest request) {
        UserMessage userMessage = UserMessage.findLast(request.messages()).orElse(null);
        if (userMessage == null || !userMessage.hasSingleText()
                || !requiresWebSearch(userMessage.singleText())) {
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
        if (hasWebSearchResult(request.messages(), webSearchToolNames)) {
            return request;
        }
        return request.toBuilder()
                .toolSpecifications(webSearchTools)
                .toolChoice(ToolChoice.REQUIRED)
                .build();
    }

    static boolean requiresWebSearch(String question) {
        return question != null && REQUIRED_PATTERN.matcher(question).find();
    }

    private static boolean isWebSearchTool(ToolSpecification toolSpecification) {
        Map<String, Object> metadata = toolSpecification.metadata();
        return metadata != null && Boolean.TRUE.equals(metadata.get(WEB_SEARCH_TOOL_METADATA_KEY));
    }

    private static boolean hasWebSearchResult(List<ChatMessage> messages, Set<String> webSearchToolNames) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message instanceof ToolExecutionResultMessage result
                    && webSearchToolNames.contains(result.toolName())) {
                return true;
            }
            if (message instanceof UserMessage) {
                return false;
            }
        }
        return false;
    }
}
