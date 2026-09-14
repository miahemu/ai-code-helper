package com.eastmoney.agent.vo.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: suyue
 * @name: ChatReqVO
 * @Date: 2026/09/01
 * @Description: AI 对答请求参数
 */
@Data
public class ChatReqVO {

    // 当前前端会话唯一标识，用于隔离不同会话的上下文记忆
    @NotBlank(message = "会话标识不能为空")
    @Size(max = 64, message = "会话标识不能超过 64 个字符")
    private String conversationId;

    // 用户问题
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题不能超过 2000 个字符")
    private String question;

    // 返回的知识库参考片段数量
    private Integer topK = 4;

    // 本次提问是否使用联网搜索
    private Boolean webSearchEnabled = false;

    // /kb 命令限定检索的文档标识，空列表表示检索全部已导入文档
    @Size(max = 20, message = "一次最多选择 20 个知识库文档")
    private List<@Size(max = 64, message = "知识库文档标识不能超过 64 个字符") String> knowledgeDocumentIds = new ArrayList<>();
}
