package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import com.eastmoney.agent.vo.response.KnowledgeReferenceRespVO;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: suyue
 * @name: ChatServiceImpl
 * @Date: 2026/09/01
 * @Description: Agent 对答与工具调用结果编排实现
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final Pattern AGENT_REFERENCE_PATTERN = Pattern.compile(
            "(?:【资料:([^】]+)】|\\[资料:([^\\]]+)])");

    private static final Pattern NUMBERED_REFERENCE_PATTERN = Pattern.compile(
            "(?:【资料(\\d+)】|\\[资料(\\d+)])");

    @Value("${agent.trace-enabled}")
    private Boolean traceEnabled;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private AgentAssistant agentAssistant;

    /**
     * 调用 Agent 生成回答，并整理知识库工具返回的引用片段
     *
     * @param request AI 对答请求参数
     * @return AI 对答结果
     */
    @Override
    public ChatRespVO chat(ChatReqVO request) {
        Result<String> agentResult = agentAssistant.chat(request.getConversationId(), request.getQuestion(),
                request.getTopK(), LocalDate.now(BUSINESS_ZONE_ID).toString());
        List<ToolExecution> toolExecutions = agentResult.toolExecutions();
        if (Boolean.TRUE.equals(traceEnabled)) {
            List<String> toolNames = toolExecutions == null ? List.of() : toolExecutions.stream()
                    .map(toolExecution -> toolExecution.request().name())
                    .toList();
            log.info("Agent 执行完成，finishReason={}，toolCallCount={}，toolNames={}",
                    agentResult.finishReason(), toolNames.size(), toolNames);
        }
        List<SearchResult> references = extractReferences(toolExecutions);
        String answer = normalizeAgentReferences(agentResult.content(), references);
        ChatRespVO result = new ChatRespVO();
        result.setAnswer(normalizeNumberedReferences(answer, references.size()));
        result.setVectorStoreMode(knowledgeService.getVectorStoreMode());
        result.setReferences(buildReferenceRespVOs(references));
        return result;
    }

    /**
     * 从 Agent 的工具执行结果中提取知识片段，并按文档和切片序号去重
     */
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

    /**
     * 将 Agent 输出的稳定引用标识转换为本次回答中的连续引用编号
     */
    private String normalizeAgentReferences(String answer, List<SearchResult> references) {
        Map<String, Integer> referenceNumberMap = new LinkedHashMap<>();
        for (int index = 0; index < references.size(); index++) {
            SearchResult reference = references.get(index);
            if (reference.getReferenceId() != null) {
                referenceNumberMap.put(reference.getReferenceId(), index + 1);
            }
        }

        Matcher matcher = AGENT_REFERENCE_PATTERN.matcher(answer == null ? "" : answer);
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

    /**
     * 统一引用标记格式，并移除不存在的引用编号
     */
    private String normalizeNumberedReferences(String answer, int referenceCount) {
        Matcher matcher = NUMBERED_REFERENCE_PATTERN.matcher(answer == null ? "" : answer);
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
