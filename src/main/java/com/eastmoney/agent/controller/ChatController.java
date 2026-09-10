package com.eastmoney.agent.controller;

import com.eastmoney.agent.base.RestResponse;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
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
     * 根据知识库检索结果生成回答
     */
    @PostMapping("ask")
    public RestResponse<?> ask(@Valid @RequestBody ChatReqVO request) {
        try {
            ChatRespVO result = chatService.chat(request);
            return RestResponse.success(result);
        } catch (Exception exception) {
            log.error("AI 对答异常", exception);
            return RestResponse.exception();
        }
    }
}
