package com.eastmoney.agent.reference;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.vo.response.KnowledgeReferenceRespVO;
import dev.langchain4j.service.tool.ToolExecution;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: suyue
 * @name: KnowledgeReferenceProcessor
 * @Date: 2026/09/11
 * @Description: 知识库资料引用提取与标记处理器
 */
@Component
public class KnowledgeReferenceProcessor {

    private static final Pattern AGENT_REFERENCE_PATTERN = Pattern.compile(
            "(?:【资料:([^】]+)】|\\[资料:([^\\]]+)])");

    private static final Pattern NUMBERED_REFERENCE_PATTERN = Pattern.compile(
            "(?:【资料(\\d+)】|\\[资料(\\d+)])");

    /**
     * 提取知识库工具返回的片段，并将回答中的资料标记转换为连续编号
     */
    public void process(ChatReferenceResult result, List<ToolExecution> toolExecutions) {
        List<SearchResult> references = extractReferences(toolExecutions);
        String answer = normalizeAgentReferences(result.getAnswer(), references);
        result.setAnswer(normalizeNumberedReferences(answer, references.size()));
        result.setReferences(buildReferenceRespVOs(references));
    }

    private List<SearchResult> extractReferences(List<ToolExecution> toolExecutions) {
        Map<String, SearchResult> referenceMap = new LinkedHashMap<>();
        if (toolExecutions == null) {
            return new ArrayList<>();
        }

        for (ToolExecution toolExecution : toolExecutions) {
            Object toolResult = toolExecution.resultObject();
            if (!(toolResult instanceof List<?>)) {
                continue;
            }
            for (Object item : (List<?>) toolResult) {
                if (item instanceof SearchResult) {
                    SearchResult reference = (SearchResult) item;
                    String key = reference.getDocumentId() + "-" + reference.getChunkIndex();
                    referenceMap.put(key, reference);
                }
            }
        }
        return new ArrayList<>(referenceMap.values());
    }

    private String normalizeAgentReferences(String answer, List<SearchResult> references) {
        Map<String, Integer> referenceNumberMap = new LinkedHashMap<>();
        for (int index = 0; index < references.size(); index++) {
            SearchResult reference = references.get(index);
            if (reference.getReferenceId() != null) {
                referenceNumberMap.put(reference.getReferenceId(), index + 1);
            }
        }

        Matcher matcher = AGENT_REFERENCE_PATTERN.matcher(answer);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String referenceId = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            Integer referenceNumber = referenceNumberMap.get(referenceId);
            String replacement = referenceNumber == null ? "" : "【资料" + referenceNumber + "】";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizeNumberedReferences(String answer, int referenceCount) {
        Matcher matcher = NUMBERED_REFERENCE_PATTERN.matcher(answer);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String numberText = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            int referenceNumber = Integer.parseInt(numberText);
            String replacement = referenceNumber > 0 && referenceNumber <= referenceCount
                    ? "【资料" + referenceNumber + "】" : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private List<KnowledgeReferenceRespVO> buildReferenceRespVOs(List<SearchResult> searchResults) {
        List<KnowledgeReferenceRespVO> references = new ArrayList<>();
        for (SearchResult searchResult : searchResults) {
            KnowledgeReferenceRespVO reference = new KnowledgeReferenceRespVO();
            BeanUtils.copyProperties(searchResult, reference);
            references.add(reference);
        }
        return references;
    }
}
