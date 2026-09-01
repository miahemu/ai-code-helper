package com.eastmoney.agent.enums;

import lombok.Getter;

/**
 * @Author: suyue
 * @name: DocumentTypeEnum
 * @Date: 2026/09/01
 * @Description: 知识库支持的文档类型
 */
@Getter
public enum DocumentTypeEnum {

    TEXT("text"),
    TXT("txt"),
    MARKDOWN("md"),
    PDF("pdf"),
    DOC("doc"),
    DOCX("docx");

    /** 文件扩展名 */
    private final String extension;

    DocumentTypeEnum(String extension) {
        this.extension = extension;
    }

    /**
     * 根据文件名识别文档类型。
     *
     * @param filename 文件名
     * @return 文档类型
     */
    public static DocumentTypeEnum fromFilename(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("无法识别文件类型");
        }
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        for (DocumentTypeEnum documentType : values()) {
            if (documentType != TEXT && documentType.extension.equals(extension)) {
                return documentType;
            }
        }
        throw new IllegalArgumentException("仅支持 txt、md、pdf、doc、docx 文件");
    }
}
