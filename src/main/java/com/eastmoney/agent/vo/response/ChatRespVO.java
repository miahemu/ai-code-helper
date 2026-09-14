package com.eastmoney.agent.vo.response;

import lombok.Data;

import java.util.List;

/**
 * @Author: suyue
 * @name: ChatRespVO
 * @Date: 2026/09/01
 * @Description: AI 对答结果
 */
@Data
public class ChatRespVO {

    // 大模型生成的回答
    private String answer;

    // 向量库模式：elasticsearch 或 sqlite
    private String vectorStoreMode;

    // 本次回答引用的知识库片段
    private List<KnowledgeReferenceRespVO> references;

    // 本次回答引用的相关网页
    private List<RelatedUrlRespVO> relatedUrls;
}
