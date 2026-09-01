package com.eastmoney.agent.service;

import com.eastmoney.agent.domain.KnowledgeChunk;
import com.eastmoney.agent.domain.SearchResult;

import java.util.List;

/**
 * @Author: suyue
 * @name: ElasticsearchService
 * @Date: 2026/09/01
 * @Description: Elasticsearch 知识切片存储与向量检索服务
 */
public interface ElasticsearchService {

    void saveAll(List<KnowledgeChunk> chunks);

    List<SearchResult> search(List<Double> queryVector, int topK);

    void deleteByDocumentId(String documentId);
}
