package com.eastmoney.agent.vo.response;

import lombok.Data;

/**
 * @Author: suyue
 * @name: KnowledgeImportRespVO
 * @Date: 2026/09/01
 * @Description: 知识库文章导入结果
 */
@Data
public class KnowledgeImportRespVO {

    /** 导入后生成的文档唯一标识 */
    private String documentId;

    /** 文档切片数量 */
    private Integer chunkCount;

    /** 向量生成模式：remote 或 local-hash */
    private String embeddingMode;

    /** 向量库模式：elasticsearch 或 memory */
    private String vectorStoreMode;
}
