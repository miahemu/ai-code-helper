package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.DocumentParseResult;
import com.eastmoney.agent.domain.KnowledgeChunk;
import com.eastmoney.agent.domain.KnowledgeDocument;
import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.enums.DocumentTypeEnum;
import com.eastmoney.agent.service.DocumentParserService;
import com.eastmoney.agent.service.EmbeddingService;
import com.eastmoney.agent.service.ElasticsearchService;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.util.TextChunkUtil;
import com.eastmoney.agent.util.VectorUtil;
import com.eastmoney.agent.vo.request.KnowledgeImportReqVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentRespVO;
import com.eastmoney.agent.vo.response.KnowledgeImportRespVO;
import com.eastmoney.agent.vo.response.SystemStatusRespVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @Author: suyue
 * @name: KnowledgeServiceImpl
 * @Date: 2026/09/01
 * @Description: 文档切分、向量化及知识库检索实现
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 未启用 Elasticsearch 时使用的内存知识库 */
    private final ConcurrentMap<String, KnowledgeChunk> localStore = new ConcurrentHashMap<>();

    /** 文档元数据和重新索引所需原文，服务重启后清空 */
    private final ConcurrentMap<String, KnowledgeDocument> documentStore = new ConcurrentHashMap<>();

    /** 文档切片大小 */
    @Value("${ai.chunk.size}")
    private Integer chunkSize;

    /** 相邻文档切片重叠长度 */
    @Value("${ai.chunk.overlap}")
    private Integer chunkOverlap;

    /** 是否启用 Elasticsearch 向量库 */
    @Value("${elasticsearch.enabled}")
    private Boolean elasticsearchEnabled;

    /** Elasticsearch 索引名称 */
    @Value("${elasticsearch.index-name}")
    private String elasticsearchIndexName;

    /** 文本向量生成服务 */
    @Autowired
    private EmbeddingService embeddingService;

    /** Elasticsearch 向量存储服务 */
    @Autowired
    private ElasticsearchService elasticsearchService;

    /** 上传文档解析服务 */
    @Autowired
    private DocumentParserService documentParserService;

    /**
     * 将文章切分并生成向量，然后写入当前启用的知识库。
     *
     * @param request 知识库文章导入参数
     * @return 文档导入结果
     */
    @Override
    public KnowledgeImportRespVO importDocument(KnowledgeImportReqVO request) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID().toString());
        document.setTitle(request.getTitle());
        document.setDocumentType(DocumentTypeEnum.TEXT);
        document.setContent(request.getContent());
        document.setCreateTime(LocalDateTime.now());
        return indexDocument(document);
    }

    /**
     * 解析上传文件并导入知识库。
     *
     * @param file 上传文件
     * @return 文档导入结果
     * @throws IOException 文件解析失败
     */
    @Override
    public KnowledgeImportRespVO importFile(MultipartFile file) throws IOException {
        DocumentParseResult parseResult = documentParserService.parse(file);
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID().toString());
        document.setTitle(parseResult.getTitle());
        document.setFilename(parseResult.getFilename());
        document.setDocumentType(parseResult.getDocumentType());
        document.setContent(parseResult.getContent());
        document.setCreateTime(LocalDateTime.now());
        return indexDocument(document);
    }

    /**
     * 查询当前服务运行期间导入的文档，并按最近索引时间倒序返回。
     *
     * @return 文档管理列表
     */
    @Override
    public List<KnowledgeDocumentRespVO> listDocuments() {
        return documentStore.values().stream()
                .sorted(Comparator.comparing(KnowledgeDocument::getUpdateTime).reversed())
                .map(document -> {
                    KnowledgeDocumentRespVO result = new KnowledgeDocumentRespVO();
                    result.setDocumentId(document.getId());
                    result.setTitle(document.getTitle());
                    result.setFilename(document.getFilename());
                    result.setDocumentType(document.getDocumentType().getExtension());
                    result.setChunkCount(document.getChunkCount());
                    result.setCreateTime(document.getCreateTime());
                    result.setUpdateTime(document.getUpdateTime());
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除文档元数据以及该文档对应的全部知识切片。
     *
     * @param documentId 文档唯一标识
     */
    @Override
    public void deleteDocument(String documentId) {
        KnowledgeDocument document = documentStore.get(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或已删除");
        }
        deleteChunks(documentId);
        documentStore.remove(documentId);
    }

    /**
     * 使用内存中保存的原始文本重新切分并生成向量，成功后替换旧切片。
     *
     * @param documentId 文档唯一标识
     * @return 重新索引结果
     */
    @Override
    public KnowledgeImportRespVO reindexDocument(String documentId) {
        KnowledgeDocument document = documentStore.get(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在，无法重新索引");
        }

        List<KnowledgeChunk> chunks = buildChunks(document);
        deleteChunks(documentId);
        saveChunks(chunks);
        document.setChunkCount(chunks.size());
        document.setUpdateTime(LocalDateTime.now());

        KnowledgeImportRespVO result = new KnowledgeImportRespVO();
        result.setDocumentId(documentId);
        result.setChunkCount(chunks.size());
        result.setEmbeddingMode(embeddingService.getMode());
        result.setVectorStoreMode(getVectorStoreMode());
        return result;
    }

    /**
     * 根据问题向量检索相似知识片段，并限制最大召回数量。
     *
     * @param question 用户问题
     * @param topK 召回片段数量
     * @return 知识库检索结果
     */
    @Override
    public List<SearchResult> search(String question, Integer topK) {
        int actualTopK = topK == null ? 4 : Math.max(1, Math.min(topK, 20));
        List<Double> queryVector = embeddingService.embed(question);
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            return elasticsearchService.search(queryVector, actualTopK);
        }
        return localStore.values().stream()
                .map(chunk -> {
                    SearchResult result = new SearchResult();
                    result.setDocumentId(chunk.getDocumentId());
                    result.setTitle(chunk.getTitle());
                    result.setChunkIndex(chunk.getChunkIndex());
                    result.setContent(chunk.getContent());
                    result.setScore(VectorUtil.cosineSimilarity(queryVector, chunk.getEmbedding()));
                    return result;
                })
                .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                .limit(actualTopK)
                .collect(Collectors.toList());
    }

    /**
     * 获取向量生成和向量库存储状态。
     *
     * @return 系统运行状态
     */
    @Override
    public SystemStatusRespVO getStatus() {
        SystemStatusRespVO result = new SystemStatusRespVO();
        result.setEmbeddingMode(embeddingService.getMode());
        result.setVectorStoreMode(getVectorStoreMode());
        result.setIndexName(elasticsearchIndexName);
        return result;
    }

    /**
     * 获取当前知识库存储模式。
     *
     * @return elasticsearch 或 memory
     */
    @Override
    public String getVectorStoreMode() {
        return Boolean.TRUE.equals(elasticsearchEnabled) ? "elasticsearch" : "memory";
    }

    /**
     * 完成首次文档切片和索引，索引成功后再保存文档元数据。
     */
    private KnowledgeImportRespVO indexDocument(KnowledgeDocument document) {
        List<KnowledgeChunk> chunks = buildChunks(document);
        saveChunks(chunks);
        document.setChunkCount(chunks.size());
        document.setUpdateTime(LocalDateTime.now());
        documentStore.put(document.getId(), document);

        KnowledgeImportRespVO result = new KnowledgeImportRespVO();
        result.setDocumentId(document.getId());
        result.setChunkCount(chunks.size());
        result.setEmbeddingMode(embeddingService.getMode());
        result.setVectorStoreMode(getVectorStoreMode());
        return result;
    }

    /**
     * 根据原文重新生成完整切片列表；全部向量成功生成后才会进入存储替换步骤。
     */
    private List<KnowledgeChunk> buildChunks(KnowledgeDocument document) {
        List<String> contents = TextChunkUtil.chunk(document.getContent(), chunkSize, chunkOverlap);
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(document.getId() + "-" + index);
            chunk.setDocumentId(document.getId());
            chunk.setTitle(document.getTitle());
            chunk.setChunkIndex(index);
            chunk.setContent(contents.get(index));
            chunk.setEmbedding(embeddingService.embed(contents.get(index)));
            chunks.add(chunk);
        }
        return chunks;
    }

    /** 将切片写入当前启用的向量存储。 */
    private void saveChunks(List<KnowledgeChunk> chunks) {
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            elasticsearchService.saveAll(chunks);
            return;
        }
        for (KnowledgeChunk chunk : chunks) {
            localStore.put(chunk.getId(), chunk);
        }
    }

    /** 删除指定文档在当前向量存储中的全部切片。 */
    private void deleteChunks(String documentId) {
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            elasticsearchService.deleteByDocumentId(documentId);
            return;
        }
        localStore.entrySet().removeIf(entry -> documentId.equals(entry.getValue().getDocumentId()));
    }
}
