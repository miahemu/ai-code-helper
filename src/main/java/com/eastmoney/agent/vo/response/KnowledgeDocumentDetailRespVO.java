package com.eastmoney.agent.vo.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: suyue
 * @name: KnowledgeDocumentDetailRespVO
 * @Date: 2026/09/10
 * @Description: 知识库文档原文详情返回对象
 */
@Data
public class KnowledgeDocumentDetailRespVO {

    // 文档唯一标识
    private String documentId;

    // 文档标题
    private String title;

    // 上传文件名
    private String filename;

    // 文档类型
    private String documentType;

    // 文档原始文本内容
    private String content;

    // 当前索引切片数量
    private Integer chunkCount;

    // 文档创建时间
    private LocalDateTime createTime;

    // 最近索引时间
    private LocalDateTime updateTime;
}
