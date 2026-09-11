package com.eastmoney.agent.service;

import com.eastmoney.agent.guardrail.SafeInputGuardrail;
import com.eastmoney.agent.guardrail.SafeOutputGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * @Author: suyue
 * @name: AgentAssistant
 * @Date: 2026/09/10
 * @Description: 支持知识库工具调用的 LangChain4j AI Service
 */
public interface AgentAssistant {

    @SystemMessage(fromResource = "prompts/agent-assistant-system.txt")
    @InputGuardrails(SafeInputGuardrail.class)
    @OutputGuardrails(value = SafeOutputGuardrail.class, maxRetries = 1)
    Result<String> chat(@MemoryId String conversationId, @UserMessage String question,
                        @V("topK") Integer topK, @V("currentDate") String currentDate);

}
