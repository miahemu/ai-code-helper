package com.eastmoney.agent.reference;

import com.eastmoney.agent.vo.response.RelatedUrlRespVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.tool.ToolExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: suyue
 * @name: WebSearchResultExtractor
 * @Date: 2026/09/11
 * @Description: 联网搜索工具结果解析器
 */
@Component
public class WebSearchResultExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s\\\"'<>\\[\\]{}]+");

    // Spring Boot 内置的 JSON 解析器，将 MCP 返回文本转换为 WebSearchResult 数组
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 从联网搜索工具结果中提取网页标题和地址，并按地址去重
     */
    public List<RelatedUrlRespVO> extract(List<ToolExecution> toolExecutions) {
        Map<String, RelatedUrlRespVO> relatedUrlMap = new LinkedHashMap<>();
        if (toolExecutions == null) {
            return new ArrayList<>();
        }
        for (ToolExecution toolExecution : toolExecutions) {
            collectToolResult(toolExecution.result(), relatedUrlMap);
        }
        return new ArrayList<>(relatedUrlMap.values());
    }

    private void collectToolResult(String toolResult,
                                   Map<String, RelatedUrlRespVO> relatedUrlMap) {
        if (toolResult == null || toolResult.isBlank()) {
            return;
        }
        try {
            WebSearchResult[] searchResults = objectMapper.readValue(
                    toolResult, WebSearchResult[].class);
            for (WebSearchResult searchResult : searchResults) {
                addRelatedUrl(relatedUrlMap, searchResult.getTitle(), searchResult.getLink());
            }
        } catch (Exception exception) {
            collectUrlsFromText(toolResult, relatedUrlMap);
        }
    }

    /**
     * 工具结果不是标准搜索列表时，仅兜底提取其中的网页地址
     */
    private void collectUrlsFromText(String text,
                                     Map<String, RelatedUrlRespVO> relatedUrlMap) {
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            addRelatedUrl(relatedUrlMap, null, matcher.group());
        }
    }

    private void addRelatedUrl(Map<String, RelatedUrlRespVO> relatedUrlMap,
                               String title, String url) {
        if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            return;
        }
        String normalizedUrl = trimUrl(url);
        if (normalizedUrl.isBlank() || relatedUrlMap.containsKey(normalizedUrl)) {
            return;
        }
        RelatedUrlRespVO relatedUrl = new RelatedUrlRespVO();
        relatedUrl.setTitle(title == null || title.isBlank() ? normalizedUrl : title);
        relatedUrl.setUrl(normalizedUrl);
        relatedUrlMap.put(normalizedUrl, relatedUrl);
    }

    static String trimUrl(String url) {
        return url.replaceAll("[),.;:!?，。；：！？]+$", "");
    }
}
