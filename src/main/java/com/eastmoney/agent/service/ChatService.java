package com.eastmoney.agent.service;

import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import com.eastmoney.agent.vo.response.ChatStreamRespVO;
import reactor.core.publisher.Flux;

/**
 * @Author: suyue
 * @name: ChatService
 * @Date: 2026/09/01
 * @Description: AI Agent 对答编排服务
 */
public interface ChatService {

    ChatRespVO chat(ChatReqVO request);

    Flux<ChatStreamRespVO> chatStream(ChatReqVO request);
}
