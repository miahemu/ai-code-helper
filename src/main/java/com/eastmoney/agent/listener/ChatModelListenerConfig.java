package com.eastmoney.agent.listener;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @Author: suyue
 * @name: ChatModelListenerConfig
 * @Date: 2026/9/14
 * @Description: 聊天模型请求、响应和异常日志监听器配置
 */
@Configuration
@Slf4j
public class ChatModelListenerConfig {

    private static final String CALL_ID_ATTRIBUTE = ChatModelListenerConfig.class.getName() + ".callId";

    private static final String START_TIME_ATTRIBUTE = ChatModelListenerConfig.class.getName() + ".startTime";

    @Value("${agent.trace-enabled:false}")
    private Boolean traceEnabled;

    /**
     * 创建聊天模型监听器，记录模型调用摘要，便于排查调用耗时、Token 使用和调用异常
     */
    @Bean
    ChatModelListener chatModelListener() {
        return new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext requestContext) {
                String callId = UUID.randomUUID().toString().substring(0, 8);
                requestContext.attributes().put(CALL_ID_ATTRIBUTE, callId);
                requestContext.attributes().put(START_TIME_ATTRIBUTE, System.nanoTime());
                if (!Boolean.TRUE.equals(traceEnabled)) {
                    return;
                }

                ChatRequest request = requestContext.chatRequest();
                log.info("AI 模型请求，callId={}，provider={}，model={}，messageCount={}，toolCount={}",
                        callId, requestContext.modelProvider(), request.modelName(), request.messages().size(),
                        request.toolSpecifications().size());
            }

            @Override
            public void onResponse(ChatModelResponseContext responseContext) {
                if (!Boolean.TRUE.equals(traceEnabled)) {
                    return;
                }

                ChatResponse response = responseContext.chatResponse();
                AiMessage aiMessage = response.aiMessage();
                List<String> toolNames = aiMessage.toolExecutionRequests().stream()
                        .map(ToolExecutionRequest::name)
                        .toList();
                TokenUsage tokenUsage = response.tokenUsage();
                String tokenSummary = tokenUsage == null ? "-" : tokenUsage.inputTokenCount() + "/"
                        + tokenUsage.outputTokenCount() + "/" + tokenUsage.totalTokenCount();
                long startTimeNanos = (long) responseContext.attributes().get(START_TIME_ATTRIBUTE);
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
                log.info("AI 模型响应，callId={}，model={}，finishReason={}，tokens(input/output/total)={}，"
                                + "toolCount={}，toolNames={}，durationMs={}",
                        responseContext.attributes().get(CALL_ID_ATTRIBUTE), response.modelName(),
                        response.finishReason(), tokenSummary, toolNames.size(), toolNames, durationMillis);
            }

            @Override
            public void onError(ChatModelErrorContext errorContext) {
                ChatRequest request = errorContext.chatRequest();
                long startTimeNanos = (long) errorContext.attributes().get(START_TIME_ATTRIBUTE);
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
                log.error("AI 模型调用异常，callId={}，provider={}，model={}，durationMs={}",
                        errorContext.attributes().get(CALL_ID_ATTRIBUTE), errorContext.modelProvider(),
                        request.modelName(), durationMillis, errorContext.error());
            }
        };
    }
}
