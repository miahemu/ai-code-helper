package com.eastmoney.agent.domain;

import com.eastmoney.agent.enums.DocumentTypeEnum;
import lombok.Data;

/**
 * @Author: suyue
 * @name: DocumentParseResult
 * @Date: 2026/09/01
 * @Description: 上传文档解析结果
 */
@Data
public class DocumentParseResult {

    /** 原始文件名 */
    private String filename;

    /** 默认文档标题 */
    private String title;

    /** 文档类型 */
    private DocumentTypeEnum documentType;

    /** 解析后的纯文本内容 */
    private String content;
}
