package com.eastmoney.agent.tool;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.KnowledgeService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: suyue
 * @name: KnowledgeSearchTool
 * @Date: 2026/09/09
 * @Description: 提供给 LangChain4j Agent 的知识库检索工具
 */
@Component
public class KnowledgeSearchTool {

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 使用现有 KnowledgeService 完成检索，避免重复实现向量化和存储访问逻辑
     */
    @Tool(name = "knowledge_search", value = "搜索用户已导入的知识库。仅在问题需要依据内部资料、文档或制度时调用；返回空列表表示没有相关内容")
    public List<SearchResult> search(
            @P(name = "query", value = "用于知识库检索的清晰、完整查询语句") String query,
            @P(name = "topK", value = "返回的知识片段数量，范围为 1 到 20") Integer topK) {
        return knowledgeService.search(query, topK);
    }
}
