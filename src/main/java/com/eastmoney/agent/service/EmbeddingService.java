package com.eastmoney.agent.service;

import dev.langchain4j.data.embedding.Embedding;

/**
 * @Author: suyue
 * @name: EmbeddingService
 * @Date: 2026/09/01
 * @Description: 文本向量生成服务
 */
public interface EmbeddingService {

    Embedding embed(String text);

    String getMode();
}
