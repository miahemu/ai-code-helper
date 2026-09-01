package com.eastmoney.agent.service;

import com.eastmoney.agent.domain.SearchResult;

import java.util.List;

/**
 * @Author: suyue
 * @name: ModelService
 * @Date: 2026/09/01
 * @Description: 大模型问答调用服务
 */
public interface ModelService {

    String chat(String question, List<SearchResult> references);
}
