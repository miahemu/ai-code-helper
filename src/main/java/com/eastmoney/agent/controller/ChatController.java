package com.eastmoney.agent.controller;

import com.eastmoney.agent.base.RestResponse;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * @Author: suyue
 * @name: ChatController
 * @Date: 2026/09/01
 * @Description: AI 对答接口
 */
@Slf4j
@Validated
@RestController
@RequestMapping("api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    /**
     * 执行 AI Agent 对答
     */
    @PostMapping("ask")
    public RestResponse<?> ask(@Valid @RequestBody ChatReqVO request) {
        try {
            ChatRespVO result = chatService.chat(request);
            return RestResponse.success(result);
        } catch (InputGuardrailException exception) {
            log.warn("用户输入未通过安全检查，message={}", exception.getMessage());
            return RestResponse.fail("问题未通过安全检查，请调整后重试");
        } catch (OutputGuardrailException exception) {
            log.warn("模型回答未通过安全检查，message={}", exception.getMessage());
            return RestResponse.fail("回答未通过安全检查，请调整问题后重试");
        } catch (Exception exception) {
            log.error("AI 对答异常", exception);
            return RestResponse.exception();
        }
    }
}
