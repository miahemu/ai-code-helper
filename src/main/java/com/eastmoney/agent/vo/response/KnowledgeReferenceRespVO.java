package com.eastmoney.agent.vo.response;

import lombok.Data;

/**
 * @Author: suyue
 * @name: KnowledgeReferenceRespVO
 * @Date: 2026/09/01
 * @Description: 对答引用的知识库片段
 */
@Data
public class KnowledgeReferenceRespVO {

    /** 原始文档唯一标识 */
    private String documentId;

    /** 原始文档标题 */
    private String title;

    /** 引用切片在原始文档中的顺序 */
    private Integer chunkIndex;

    /** 引用的切片内容 */
    private String content;

    /** 引用切片与问题的相似度得分 */
    private Double score;
}
