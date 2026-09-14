package com.eastmoney.agent.service;

import com.eastmoney.agent.guardrail.SafeInputGuardrail;
import com.eastmoney.agent.guardrail.SafeOutputGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

import java.util.List;

/**
 * @Author: suyue
 * @name: AgentAssistant
 * @Date: 2026/09/10
 * @Description: 支持知识库工具调用的 LangChain4j AI Service
 */
public interface AgentAssistant {

    @SystemMessage(fromResource = "prompts/agent-assistant-system.txt")
    @InputGuardrails(SafeInputGuardrail.class)
    @OutputGuardrails(value = SafeOutputGuardrail.class, maxRetries = 2)
    Result<String> chat(@MemoryId String conversationId, @UserMessage String question,
                        @V("topK") Integer topK,
                        @V("knowledgeDocumentIds") List<String> knowledgeDocumentIds);

    /**
     * 流式回答不配置输出护轨，避免完整回答校验导致响应分片被缓存到生成结束后才发送
     */
    @SystemMessage(fromResource = "prompts/agent-assistant-system.txt")
    @InputGuardrails(SafeInputGuardrail.class)
    TokenStream chatStream(@MemoryId String conversationId, @UserMessage String question,
                           @V("topK") Integer topK,
                           @V("knowledgeDocumentIds") List<String> knowledgeDocumentIds);

}
