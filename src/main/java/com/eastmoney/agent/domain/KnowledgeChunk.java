package com.eastmoney.agent.domain;

import lombok.Data;

import java.util.List;

/**
 * @Author: suyue
 * @name: KnowledgeChunk
 * @Date: 2026/09/01
 * @Description: 知识库文档切片及其向量数据
 */
@Data
public class KnowledgeChunk {

    // 知识切片唯一标识
    private String id;

    // 原始文档唯一标识
    private String documentId;

    // 原始文档标题
    private String title;

    // 切片在原始文档中的顺序
    private Integer chunkIndex;

    // 切片文本内容
    private String content;

    // 切片文本对应的向量
    private List<Double> embedding;
}
