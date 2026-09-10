package com.eastmoney.agent.domain;

import lombok.Data;

/**
 * @Author: suyue
 * @name: SearchResult
 * @Date: 2026/09/01
 * @Description: 知识库向量检索结果
 */
@Data
public class SearchResult {

    // Agent 引用知识片段时使用的稳定标识
    private String referenceId;

    // 原始文档唯一标识
    private String documentId;

    // 原始文档标题
    private String title;

    // 命中切片在原始文档中的顺序
    private Integer chunkIndex;

    // 命中的切片内容
    private String content;

    // 向量相似度得分
    private Double score;
}
