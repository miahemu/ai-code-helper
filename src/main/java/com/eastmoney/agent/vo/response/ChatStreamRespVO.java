package com.eastmoney.agent.vo.response;

import lombok.Data;

/**
 * @Author: suyue
 * @name: ChatStreamRespVO
 * @Date: 2026/09/14
 * @Description: AI 对答 SSE 流式事件
 */
@Data
public class ChatStreamRespVO {

    // 事件类型：content、complete 或 error
    private String type;

    // content 和 error 事件携带的文本内容
    private String content;

    // complete 事件携带的完整对答结果
    private ChatRespVO result;
}
