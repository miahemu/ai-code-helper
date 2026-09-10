package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.service.FixedRagAssistant;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: suyue
 * @name: ChatServiceImpl
 * @Date: 2026/09/01
 * @Description: 知识检索与模型回答编排实现
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    @Value("${agent.enabled:false}")
    private Boolean agentEnabled;

    @Value("${agent.trace-enabled:false}")
    private Boolean traceEnabled;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private FixedRagAssistant fixedRagAssistant;

    @Autowired
    private AgentAssistant agentAssistant;

    /**
     * 检索与用户问题相关的知识片段，并调用模型生成最终回答
     *
     * @param request AI 对答请求参数
     * @return AI 对答结果
     */
    @Override
    public ChatRespVO chat(ChatReqVO request) {
        if (Boolean.TRUE.equals(agentEnabled)) {
            return this.agentChat(request);
        }
        return this.fixedRagChat(request);
    }

    /**
     * 执行原有固定知识检索和模型回答流程
     */
    private ChatRespVO fixedRagChat(ChatReqVO request) {
        List<SearchResult> searchResults = knowledgeService.search(request.getQuestion(), request.getTopK());
        String answer = fixedRagAssistant.chat(request.getConversationId(), request.getQuestion(), buildReferenceContent(searchResults));
        ChatRespVO result = new ChatRespVO();
        result.setAnswer(answer);
        result.setVectorStoreMode(knowledgeService.getVectorStoreMode());
        result.setReferences(buildReferenceRespVOs(searchResults));
        return result;
    }

    /**
     * 执行由模型自主选择工具的 Agent 流程
     */
    private ChatRespVO agentChat(ChatReqVO request) {
        Result<String> agentResult = agentAssistant.chat(request.getConversationId(), request.getQuestion(), request.getTopK());
        List<ToolExecution> toolExecutions = agentResult.toolExecutions();
        if (Boolean.TRUE.equals(traceEnabled)) {
            log.info("Agent 执行完成，finishReason={}，toolCallCount={}",
                    agentResult.finishReason(), toolExecutions == null ? 0 : toolExecutions.size());
        }
        ChatRespVO result = new ChatRespVO();
        result.setAnswer(agentResult.content());
        result.setVectorStoreMode(knowledgeService.getVectorStoreMode());
        result.setReferences(buildReferenceRespVOs(extractReferences(toolExecutions)));
        return result;
    }


    /**
     * 固定 RAG 模式下，将召回片段整理为模型提示词中的参考资料
     */
    private String buildReferenceContent(List<SearchResult> searchResults) {
        if (searchResults.isEmpty()) {
            return "未检索到相关知识库片段";
        }

        StringBuilder content = new StringBuilder();
        for (int index = 0; index < searchResults.size(); index++) {
            SearchResult searchResult = searchResults.get(index);
            content.append("[资料").append(index + 1).append("] 标题：")
                    .append(searchResult.getTitle()).append("\n")
                    .append(searchResult.getContent()).append("\n\n");
        }
        return content.toString();
    }

    /**
     * 从 Agent 的工具执行结果中提取实际使用过的知识片段，并按文档和切片序号去重
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
