package com.eastmoney.agent.reference;

import dev.langchain4j.service.tool.ToolExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: suyue
 * @name: ChatReferenceProcessor
 * @Date: 2026/09/11
 * @Description: 对答知识库资料与相关网址引用处理器
 */
@Component
public class ChatReferenceProcessor {

    @Autowired
    private KnowledgeReferenceProcessor knowledgeReferenceProcessor;

    @Autowired
    private WebSearchReferenceProcessor webSearchReferenceProcessor;

    /**
     * 提取本次工具调用产生的引用，并规范化回答中的引用标记
     *
     * @param answer Agent 原始回答
     * @param toolExecutions Agent 工具执行结果
     * @return 对答引用处理结果
     */
    public ChatReferenceResult process(String answer, List<ToolExecution> toolExecutions) {
        ChatReferenceResult result = new ChatReferenceResult();
        result.setAnswer(answer == null ? "" : answer);
        knowledgeReferenceProcessor.process(result, toolExecutions);
        webSearchReferenceProcessor.process(result, toolExecutions);
        return result;
    }
}
