package com.eastmoney.agent.domain;

import com.eastmoney.agent.enums.DocumentTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: suyue
 * @name: KnowledgeDocument
 * @Date: 2026/09/01
 * @Description: 知识库文档及其索引状态
 */
@Data
public class KnowledgeDocument {

    /** 文档唯一标识 */
    private String id;

    /** 文档标题 */
    private String title;

    /** 上传文件名，粘贴导入时为空 */
    private String filename;

    /** 文档类型 */
    private DocumentTypeEnum documentType;

    /** 用于重新索引的原始文本 */
    private String content;

    /** 当前索引切片数量 */
    private Integer chunkCount;

    /** 文档创建时间 */
    private LocalDateTime createTime;

    /** 最近索引时间 */
    private LocalDateTime updateTime;
}
