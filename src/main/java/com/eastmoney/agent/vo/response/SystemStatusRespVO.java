package com.eastmoney.agent.vo.response;

import lombok.Data;

/**
 * @Author: suyue
 * @name: SystemStatusRespVO
 * @Date: 2026/09/01
 * @Description: Demo 当前模型与向量库运行模式
 */
@Data
public class SystemStatusRespVO {

    // 向量生成模式：remote 或 local-hash
    private String embeddingMode;

    // 向量库模式：elasticsearch 或 memory
    private String vectorStoreMode;

    // Elasticsearch 索引名称
    private String indexName;
}
