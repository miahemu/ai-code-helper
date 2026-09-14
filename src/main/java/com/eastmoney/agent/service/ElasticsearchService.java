package com.eastmoney.agent.service;

import com.eastmoney.agent.domain.KnowledgeChunk;
import com.eastmoney.agent.domain.SearchResult;
import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

/**
 * @Author: suyue
 * @name: ElasticsearchService
 * @Date: 2026/09/01
 * @Description: Elasticsearch 知识切片存储与向量检索服务
 */
public interface ElasticsearchService {

    void saveAll(List<KnowledgeChunk> chunks);

    List<SearchResult> search(Embedding queryEmbedding, int topK);

    List<SearchResult> search(Embedding queryEmbedding, int topK, List<String> documentIds);

    void deleteByDocumentId(String documentId);
}
