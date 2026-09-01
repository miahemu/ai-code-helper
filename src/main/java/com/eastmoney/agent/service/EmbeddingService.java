package com.eastmoney.agent.service;

import java.util.List;

/**
 * @Author: suyue
 * @name: EmbeddingService
 * @Date: 2026/09/01
 * @Description: 文本向量生成服务
 */
public interface EmbeddingService {

    List<Double> embed(String text);

    String getMode();
}

