package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.service.EmbeddingService;
import com.eastmoney.agent.util.VectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: suyue
 * @name: EmbeddingServiceImpl
 * @Date: 2026/09/01
 * @Description: OpenAI 兼容或本地哈希文本向量实现
 */
@Service
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    /** OpenAI 兼容的 Embeddings 完整地址 */
    @Value("${ai.embedding.url}")
    private String embeddingUrl;

    /** Embedding 接口认证密钥 */
    @Value("${ai.embedding.api-key}")
    private String embeddingApiKey;

    /** Embedding 模型名称 */
    @Value("${ai.embedding.model}")
    private String embeddingModel;

    /** 向量维度 */
    @Value("${ai.embedding.dimensions}")
    private Integer embeddingDimensions;

    /** HTTP 请求客户端 */
    @Autowired
    private RestTemplate restTemplate;

    /**
     * 生成文本向量。配置远程 Embedding 服务时调用远程接口，
     * 否则使用本地哈希向量保证演示功能可运行
     *
     * @param text 待向量化文本
     * @return 文本向量
     */
    @Override
    public List<Double> embed(String text) {
        if (!StringUtils.hasText(embeddingUrl)) {
            return VectorUtil.hashEmbedding(text, embeddingDimensions);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(embeddingApiKey)) {
            headers.setBearerAuth(embeddingApiKey);
        }
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", embeddingModel);
        requestBody.put("input", text);

        Map response = restTemplate.postForObject(
                embeddingUrl, new HttpEntity<>(requestBody, headers), Map.class);
        List<Double> vector = parseVector(response);
        if (vector.size() != embeddingDimensions) {
            throw new IllegalStateException("Embedding 返回维度为 " + vector.size()
                    + "，与 ai.embedding.dimensions=" + embeddingDimensions + " 不一致");
        }
        log.debug("Remote embedding generated, dimensions={}", vector.size());
        return vector;
    }

    /**
     * 从 OpenAI Embeddings 响应中解析第一条向量数据
     *
     * @param response Embedding 接口响应
     * @return 向量数据
     */
    private List<Double> parseVector(Map response) {
        if (response == null || !(response.get("data") instanceof List)) {
            throw new IllegalStateException("Embedding 接口返回格式不正确：缺少 data");
        }
        List data = (List) response.get("data");
        if (data.isEmpty() || !(data.get(0) instanceof Map)) {
            throw new IllegalStateException("Embedding 接口返回格式不正确：data 为空");
        }
        Object embedding = ((Map) data.get(0)).get("embedding");
        if (!(embedding instanceof List)) {
            throw new IllegalStateException("Embedding 接口返回格式不正确：缺少 embedding");
        }
        List<Double> result = new ArrayList<>();
        for (Object value : (List) embedding) {
            result.add(((Number) value).doubleValue());
        }
        return result;
    }

    /**
     * 获取当前向量生成模式
     *
     * @return remote 或 local-hash
     */
    @Override
    public String getMode() {
        return StringUtils.hasText(embeddingUrl) ? "remote" : "local-hash";
    }
}
