package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.reference.ChatReferenceProcessor;
import com.eastmoney.agent.reference.ChatReferenceResult;
import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

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

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private AgentAssistant agentAssistant;

    @Autowired
    private ChatReferenceProcessor chatReferenceProcessor;

    /**
     * 调用 Agent 生成回答，并整理本次回答引用的资料和相关网址
     *
     * @param request AI 对答请求参数
     * @return AI 对答结果
     */
    @Override
    public ChatRespVO chat(ChatReqVO request) {
        Result<String> agentResult = agentAssistant.chat(request.getConversationId(), request.getQuestion(),
                request.getTopK(), LocalDate.now(BUSINESS_ZONE_ID).toString());
        List<ToolExecution> toolExecutions = agentResult.toolExecutions();
        ChatReferenceResult referenceResult = chatReferenceProcessor.process(agentResult.content(), toolExecutions);
        ChatRespVO result = new ChatRespVO();
        result.setAnswer(referenceResult.getAnswer());
        result.setVectorStoreMode(knowledgeService.getVectorStoreMode());
        result.setReferences(referenceResult.getReferences());
        result.setRelatedUrls(referenceResult.getRelatedUrls());
        return result;
    }
}
