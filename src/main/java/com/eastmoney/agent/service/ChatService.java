package com.eastmoney.agent.service;

import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;

/**
 * @Author: suyue
 * @name: ChatService
 * @Date: 2026/09/01
 * @Description: AI Agent 对答编排服务
 */
public interface ChatService {

    ChatRespVO chat(ChatReqVO request);
}
