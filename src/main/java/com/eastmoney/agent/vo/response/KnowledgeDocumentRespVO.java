package com.eastmoney.agent.vo.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: suyue
 * @name: KnowledgeDocumentRespVO
 * @Date: 2026/09/01
 * @Description: 知识库文档管理列表返回对象
 */
@Data
public class KnowledgeDocumentRespVO {

    // 文档唯一标识
    private String documentId;

    // 文档标题
    private String title;

    // 上传文件名
    private String filename;

    // 文档类型
    private String documentType;

    // 当前索引切片数量
    private Integer chunkCount;

    // 文档创建时间
    private LocalDateTime createTime;

    // 最近索引时间
    private LocalDateTime updateTime;
}
