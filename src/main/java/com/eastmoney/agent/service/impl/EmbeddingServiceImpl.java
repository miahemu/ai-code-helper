package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.service.EmbeddingService;
import com.eastmoney.agent.util.VectorUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
/**
 * @Author: suyue
 * @name: EmbeddingServiceImpl
 * @Date: 2026/09/01
 * @Description: 基于 LangChain4j 的远程向量或本地哈希向量实现
 */
@Service
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    /** OpenAI 兼容的 Embeddings 完整地址 */
    @Value("${ai.embedding.url}")
    private String embeddingUrl;

    /** Embedding 接口认证密钥 */
    @Value("${ai.embedding.api-key}")
    private String embeddingApiKey;

    /** Embedding 模型名称 */
    @Value("${ai.embedding.model}")
    private String embeddingModelName;

    /** 向量维度 */
    @Value("${ai.embedding.dimensions}")
    private Integer embeddingDimensions;

    @Value("${http.read-timeout}")
    private Integer readTimeout;

    private EmbeddingModel embeddingModel;

    /**
     * 仅在配置远程 Embedding 地址时创建模型，本地演示模式继续使用哈希向量兜底
     */
    @PostConstruct
    public void initEmbeddingModel() {
        if (!StringUtils.hasText(embeddingUrl)) {
            return;
        }
        embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl(resolveBaseUrl(embeddingUrl))
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName)
                .timeout(Duration.ofMillis(readTimeout))
                .build();
    }

    /**
     * 生成文本向量。配置远程 Embedding 服务时由 LangChain4j 调用，
     * 否则使用本地哈希向量保证演示功能可运行
     *
     * @param text 待向量化文本
     * @return 文本向量
     */
    @Override
    public Embedding embed(String text) {
        if (embeddingModel == null) {
            return VectorUtil.hashEmbedding(text, embeddingDimensions);
        }

        Embedding embedding = embeddingModel.embed(text).content();
        if (embedding.dimension() != embeddingDimensions) {
            throw new IllegalStateException("Embedding 返回维度为 " + embedding.dimension()
                    + "，与 ai.embedding.dimensions=" + embeddingDimensions + " 不一致");
        }

        log.debug("Remote embedding generated, dimensions={}", embedding.dimension());
        return embedding;
    }

    /**
     * 获取当前向量生成模式
     *
     * @return remote 或 local-hash
     */
    @Override
    public String getMode() {
        return embeddingModel == null ? "local-hash" : "remote";
    }

    private String resolveBaseUrl(String endpoint) {
        String baseUrl = endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (baseUrl.endsWith(EMBEDDINGS_PATH)) {
            return baseUrl.substring(0, baseUrl.length() - EMBEDDINGS_PATH.length());
        }
        return baseUrl;
    }
}
