package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.reference.ChatReferenceProcessor;
import com.eastmoney.agent.reference.ChatReferenceResult;
import com.eastmoney.agent.service.AgentAssistant;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import com.eastmoney.agent.vo.response.ChatStreamRespVO;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: suyue
 * @name: ChatServiceImpl
 * @Date: 2026/09/01
 * @Description: Agent 对答与工具调用结果编排实现
 */
@Service
public class ChatServiceImpl implements ChatService {

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
                request.getTopK());
        return buildChatResponse(agentResult.content(), agentResult.toolExecutions());
    }

    /**
     * 订阅 Agent 流式响应，将回答分片和包含引用信息的最终结果转换为 SSE 事件
     *
     * @param request AI 对答请求参数
     * @return AI 对答流式事件
     */
    @Override
    public Flux<ChatStreamRespVO> chatStream(ChatReqVO request) {
        return Flux.create(sink -> {
            List<ToolExecution> toolExecutions = new ArrayList<>();
            TokenStream tokenStream = agentAssistant.chatStream(request.getConversationId(), request.getQuestion(),
                    request.getTopK());
            tokenStream.onPartialResponse(content -> {
                        ChatStreamRespVO event = new ChatStreamRespVO();
                        event.setType("content");
                        event.setContent(content);
                        sink.next(event);
                    })
                    .onToolExecuted(toolExecutions::add)
                    .onCompleteResponse(response -> {
                        ChatStreamRespVO event = new ChatStreamRespVO();
                        event.setType("complete");
                        event.setResult(buildChatResponse(response.aiMessage().text(), toolExecutions));
                        sink.next(event);
                        sink.complete();
                    })
                    .onError(sink::error)
                    .start();
        });
    }

    private ChatRespVO buildChatResponse(String answer, List<ToolExecution> toolExecutions) {
        ChatReferenceResult referenceResult = chatReferenceProcessor.process(answer, toolExecutions);
        ChatRespVO result = new ChatRespVO();
        result.setAnswer(referenceResult.getAnswer());
        result.setVectorStoreMode(knowledgeService.getVectorStoreMode());
        result.setReferences(referenceResult.getReferences());
        result.setRelatedUrls(referenceResult.getRelatedUrls());
        return result;
    }
}
