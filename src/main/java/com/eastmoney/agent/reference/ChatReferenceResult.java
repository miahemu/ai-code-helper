package com.eastmoney.agent.reference;

import com.eastmoney.agent.vo.response.KnowledgeReferenceRespVO;
import com.eastmoney.agent.vo.response.RelatedUrlRespVO;
import lombok.Data;

import java.util.List;

/**
 * @Author: suyue
 * @name: ChatReferenceResult
 * @Date: 2026/09/11
 * @Description: 对答正文及引用处理结果
 */
@Data
public class ChatReferenceResult {

    // 规范化引用标记后的回答正文
    private String answer;

    // 本次回答引用的知识库片段
    private List<KnowledgeReferenceRespVO> references;

    // 本次回答引用的相关网页
    private List<RelatedUrlRespVO> relatedUrls;
}
