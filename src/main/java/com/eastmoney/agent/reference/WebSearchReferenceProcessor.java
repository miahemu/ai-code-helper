package com.eastmoney.agent.reference;

import com.eastmoney.agent.vo.response.RelatedUrlRespVO;
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
 * @name: WebSearchReferenceProcessor
 * @Date: 2026/09/11
 * @Description: 联网搜索引用提取与标记处理器
 */
@Component
public class WebSearchReferenceProcessor {

    private static final Pattern MARKDOWN_URL_PATTERN = Pattern.compile(
            "(?:(?:【|\\[)\\s*(?:来源|参考|出处)\\s*[:：]?\\s*)?"
                    + "\\[([^\\]\\r\\n]+)]\\((https?://[^\\s)]+)\\)"
                    + "(?:\\s*(?:】|]))?");

    @Autowired
    private WebSearchResultExtractor webSearchResultExtractor;

    /**
     * 提取联网搜索结果，并将回答中的网页链接转换为连续编号
     */
    public void process(ChatReferenceResult result, List<ToolExecution> toolExecutions) {
        List<RelatedUrlRespVO> searchedUrls = webSearchResultExtractor.extract(toolExecutions);
        List<RelatedUrlRespVO> relatedUrls = new ArrayList<>();
        result.setAnswer(normalizeReferences(result.getAnswer(), searchedUrls, relatedUrls));
        result.setRelatedUrls(relatedUrls);
    }

    private String normalizeReferences(String answer, List<RelatedUrlRespVO> searchedUrls,
                                       List<RelatedUrlRespVO> relatedUrls) {
        Map<String, RelatedUrlRespVO> searchedUrlMap = new LinkedHashMap<>();
        for (RelatedUrlRespVO searchedUrl : searchedUrls) {
            searchedUrlMap.put(searchedUrl.getUrl(), searchedUrl);
        }
        Map<String, Integer> referenceNumberMap = new LinkedHashMap<>();
        Matcher matcher = MARKDOWN_URL_PATTERN.matcher(answer);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            String url = WebSearchResultExtractor.trimUrl(matcher.group(2));
            Integer referenceNumber = referenceNumberMap.get(url);
            if (referenceNumber == null) {
                RelatedUrlRespVO searchedUrl = searchedUrlMap.get(url);
                RelatedUrlRespVO relatedUrl = new RelatedUrlRespVO();
                relatedUrl.setTitle(title.isBlank() && searchedUrl != null
                        ? searchedUrl.getTitle() : title);
                relatedUrl.setUrl(url);
                relatedUrls.add(relatedUrl);
                referenceNumber = relatedUrls.size();
                referenceNumberMap.put(url, referenceNumber);
            }
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement("【相关网址" + referenceNumber + "】"));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
