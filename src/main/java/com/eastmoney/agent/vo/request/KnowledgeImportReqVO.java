package com.eastmoney.agent.vo.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @Author: suyue
 * @name: KnowledgeImportReqVO
 * @Date: 2026/09/01
 * @Description: 知识库文章导入请求参数
 */
@Data
public class KnowledgeImportReqVO {

    // 文章标题
    @NotBlank(message = "文章标题不能为空")
    @Size(max = 200, message = "文章标题不能超过 200 个字符")
    private String title;

    // 文章正文内容
    @NotBlank(message = "文章内容不能为空")
    private String content;
}
