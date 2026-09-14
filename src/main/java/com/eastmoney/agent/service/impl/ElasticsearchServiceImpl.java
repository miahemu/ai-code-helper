package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.KnowledgeChunk;
import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.ElasticsearchService;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: suyue
 * @name: ElasticsearchServiceImpl
 * @Date: 2026/09/01
 * @Description: 基于 Elasticsearch dense_vector 的知识库实现
 */
@Service
public class ElasticsearchServiceImpl implements ElasticsearchService {

    @Value("${elasticsearch.url}")
    private String elasticsearchUrl;

    @Value("${elasticsearch.username}")
    private String elasticsearchUsername;

    @Value("${elasticsearch.password}")
    private String elasticsearchPassword;

    @Value("${elasticsearch.index-name}")
    private String elasticsearchIndexName;

    @Value("${ai.embedding.dimensions}")
    private Integer embeddingDimensions;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 批量保存知识切片，并在写入完成后刷新索引
     *
     * @param chunks 待保存的知识切片
     */
    @Override
    public void saveAll(List<KnowledgeChunk> chunks) {
        ensureIndex();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("documentId", chunk.getDocumentId());
            document.put("title", chunk.getTitle());
            document.put("chunkIndex", chunk.getChunkIndex());
            document.put("content", chunk.getContent());
            document.put("embedding", chunk.getEmbedding().vectorAsList());
            exchange(HttpMethod.PUT, "/" + elasticsearchIndexName + "/_doc/" + chunk.getId(), document);
        }
        exchange(HttpMethod.POST, "/" + elasticsearchIndexName + "/_refresh", null);
    }

    /**
     * 使用 cosineSimilarity 脚本执行向量相似度检索
     *
     * @param queryEmbedding 问题向量
     * @param topK 召回片段数量
     * @return 知识库检索结果
     */
    @Override
    public List<SearchResult> search(Embedding queryEmbedding, int topK) {
        return search(queryEmbedding, topK, null);
    }

    /**
     * 使用 cosineSimilarity 脚本在指定文档范围内执行向量相似度检索
     *
     * @param queryEmbedding 问题向量
     * @param topK 召回片段数量
     * @param documentIds 限定检索的文档标识
     * @return 知识库检索结果
     */
    @Override
    public List<SearchResult> search(Embedding queryEmbedding, int topK, List<String> documentIds) {
        ensureIndex();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("queryVector", queryEmbedding.vectorAsList());

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("source", "cosineSimilarity(params.queryVector, 'embedding') + 1.0");
        script.put("params", params);

        Map<String, Object> scriptScore = new LinkedHashMap<>();
        if (documentIds == null || documentIds.isEmpty()) {
            scriptScore.put("query", Collections.singletonMap("match_all", Collections.emptyMap()));
        } else {
            Map<String, Object> terms = Collections.singletonMap("documentId", documentIds);
            scriptScore.put("query", Collections.singletonMap("terms", terms));
        }
        scriptScore.put("script", script);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", topK);
        body.put("_source", new String[]{"documentId", "title", "chunkIndex", "content"});
        body.put("query", Collections.singletonMap("script_score", scriptScore));

        Map response = exchange(HttpMethod.POST, "/" + elasticsearchIndexName + "/_search", body);
        return parseSearchResults(response);
    }

    /**
     * 根据文档标识删除其全部知识切片，并立即刷新索引
     *
     * @param documentId 文档唯一标识
     */
    @Override
    public void deleteByDocumentId(String documentId) {
        Map<String, Object> term = Collections.singletonMap("documentId", documentId);
        Map<String, Object> query = Collections.singletonMap("term", term);
        Map<String, Object> body = Collections.singletonMap("query", query);
        exchange(HttpMethod.POST,
                "/" + elasticsearchIndexName + "/_delete_by_query?refresh=true", body);
    }

    /**
     * 检查向量索引是否存在，不存在时按照当前向量维度创建索引映射
     */
    private void ensureIndex() {
        try {
            exchange(HttpMethod.HEAD, "/" + elasticsearchIndexName, null);
            return;
        } catch (HttpStatusCodeException exception) {
            if (exception.getRawStatusCode() != 404) {
                throw exception;
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("documentId", Collections.singletonMap("type", "keyword"));
        properties.put("title", Collections.singletonMap("type", "text"));
        properties.put("chunkIndex", Collections.singletonMap("type", "integer"));
        properties.put("content", Collections.singletonMap("type", "text"));

        Map<String, Object> vectorMapping = new LinkedHashMap<>();
        vectorMapping.put("type", "dense_vector");
        vectorMapping.put("dims", embeddingDimensions);
        vectorMapping.put("index", false);
        properties.put("embedding", vectorMapping);

        Map<String, Object> mappings = Collections.singletonMap("properties", properties);
        exchange(HttpMethod.PUT, "/" + elasticsearchIndexName, Collections.singletonMap("mappings", mappings));
    }

    /**
     * 将 Elasticsearch 查询响应转换为业务检索结果
     *
     * @param response Elasticsearch 查询响应
     * @return 知识库检索结果
     */
    private List<SearchResult> parseSearchResults(Map response) {
        if (response == null || !(response.get("hits") instanceof Map)) {
            return Collections.emptyList();
        }
        Object hitsValue = ((Map) response.get("hits")).get("hits");
        if (!(hitsValue instanceof List)) {
            return Collections.emptyList();
        }
        List<SearchResult> result = new ArrayList<>();
        for (Object hitValue : (List) hitsValue) {
            Map hit = (Map) hitValue;
            Map source = (Map) hit.get("_source");
            SearchResult item = new SearchResult();
            item.setDocumentId(String.valueOf(source.get("documentId")));
            item.setTitle(String.valueOf(source.get("title")));
            item.setChunkIndex(((Number) source.get("chunkIndex")).intValue());
            item.setContent(String.valueOf(source.get("content")));
            item.setScore(hit.get("_score") == null ? 0D : ((Number) hit.get("_score")).doubleValue() - 1D);
            result.add(item);
        }
        return result;
    }

    /**
     * 统一发送 Elasticsearch HTTP 请求，并处理基础认证和服务地址
     *
     * @param method HTTP 请求方法
     * @param path Elasticsearch API 路径
     * @param body 请求体
     * @return Elasticsearch 响应数据
     */
    private Map exchange(HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(elasticsearchUsername)) {
            String token = elasticsearchUsername + ":" + elasticsearchPassword;
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                    .encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        String baseUrl = elasticsearchUrl.endsWith("/")
                ? elasticsearchUrl.substring(0, elasticsearchUrl.length() - 1)
                : elasticsearchUrl;
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl + path, method, entity, Map.class);
        return response.getBody();
    }
}
