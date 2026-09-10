package com.eastmoney.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * @Author: suyue
 * @name: FixedRagAssistant
 * @Date: 2026/09/10
 * @Description: 固定检索流程使用的 LangChain4j AI Service
 */
public interface FixedRagAssistant {

    @SystemMessage(fromResource = "prompts/fixed-rag-assistant-system.txt")
    @UserMessage(fromResource = "prompts/fixed-rag-assistant-user.txt")
    String chat(@MemoryId String conversationId, @V("question") String question, @V("references") String references);

}
