package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.DocumentParseResult;
import com.eastmoney.agent.domain.KnowledgeChunk;
import com.eastmoney.agent.domain.KnowledgeDocument;
import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.enums.DocumentTypeEnum;
import com.eastmoney.agent.repository.KnowledgeRepository;
import com.eastmoney.agent.service.DocumentParserService;
import com.eastmoney.agent.service.EmbeddingService;
import com.eastmoney.agent.service.ElasticsearchService;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.vo.request.KnowledgeImportReqVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentDetailRespVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentRespVO;
import com.eastmoney.agent.vo.response.KnowledgeImportRespVO;
import com.eastmoney.agent.vo.response.SystemStatusRespVO;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Author: suyue
 * @name: KnowledgeServiceImpl
 * @Date: 2026/09/01
 * @Description: 文档切分、向量化及知识库检索实现
 */
@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final String LOCAL_HASH_MODE = "local-hash";

    private static final Pattern LATIN_KEYWORD_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private static final Pattern CHINESE_TEXT_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]+");

    private static final Set<String> QUERY_STOP_WORDS = new HashSet<>(Arrays.asList(
            "什么", "怎么", "如何", "哪些", "是否", "可以", "需要", "应该", "一个",
            "这个", "那个", "有关", "相关", "问题", "一下", "介绍", "说明", "告诉", "帮我"));

    // 文档切片大小
    @Value("${ai.chunk.size}")
    private Integer chunkSize;

    // 相邻文档切片重叠长度
    @Value("${ai.chunk.overlap}")
    private Integer chunkOverlap;

    // 知识切片最低相似度，低于该值时不参与回答
    @Value("${ai.retrieval.min-score:0.20}")
    private Double retrievalMinScore;

    // 本地哈希向量容易因维度碰撞产生假相似，需要使用真实关键词重合进行兜底
    @Value("${ai.retrieval.local-keyword-filter-enabled:true}")
    private Boolean localKeywordFilterEnabled;

    @Value("${elasticsearch.enabled}")
    private Boolean elasticsearchEnabled;

    @Value("${elasticsearch.index-name}")
    private String elasticsearchIndexName;

    @Value("${bigmodel.enabled:false}")
    private Boolean webSearchEnabled;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private DocumentParserService documentParserService;

    @Autowired
    private KnowledgeRepository knowledgeRepository;

    /**
     * 将文章切分并生成向量，然后写入当前启用的知识库
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
     * 解析上传文件并导入知识库
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
     * 查询当前服务运行期间导入的文档，并按最近索引时间倒序返回
     *
     * @return 文档管理列表
     */
    @Override
    public List<KnowledgeDocumentRespVO> listDocuments() {
        return knowledgeRepository.listDocuments().stream()
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
     * 查询指定文档的元数据和原始文本，用于页面查看文档内容
     *
     * @param documentId 文档唯一标识
     * @return 文档详情
     */
    @Override
    public KnowledgeDocumentDetailRespVO getDocument(String documentId) {
        KnowledgeDocument document = knowledgeRepository.findDocument(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或已删除");
        }

        KnowledgeDocumentDetailRespVO result = new KnowledgeDocumentDetailRespVO();
        result.setDocumentId(document.getId());
        result.setTitle(document.getTitle());
        result.setFilename(document.getFilename());
        result.setDocumentType(document.getDocumentType().getExtension());
        result.setContent(document.getContent());
        result.setChunkCount(document.getChunkCount());
        result.setCreateTime(document.getCreateTime());
        result.setUpdateTime(document.getUpdateTime());
        return result;
    }

    /**
     * 删除文档元数据以及该文档对应的全部知识切片
     *
     * @param documentId 文档唯一标识
     */
    @Override
    public void deleteDocument(String documentId) {
        KnowledgeDocument document = knowledgeRepository.findDocument(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在或已删除");
        }
        deleteChunks(documentId);
        knowledgeRepository.deleteDocument(documentId);
    }

    /**
     * 使用内存中保存的原始文本重新切分并生成向量，成功后替换旧切片
     *
     * @param documentId 文档唯一标识
     * @return 重新索引结果
     */
    @Override
    public KnowledgeImportRespVO reindexDocument(String documentId) {
        KnowledgeDocument document = knowledgeRepository.findDocument(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在，无法重新索引");
        }

        List<KnowledgeChunk> chunks = this.buildChunks(document);
        document.setChunkCount(chunks.size());
        document.setUpdateTime(LocalDateTime.now());
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            deleteChunks(documentId);
        }
        saveIndex(document, chunks);

        KnowledgeImportRespVO result = new KnowledgeImportRespVO();
        result.setDocumentId(documentId);
        result.setChunkCount(chunks.size());
        result.setEmbeddingMode(embeddingService.getMode());
        result.setVectorStoreMode(getVectorStoreMode());
        return result;
    }

    /**
     * 根据问题向量检索相似知识片段，并限制最大召回数量
     *
     * @param question 用户问题
     * @param topK 召回片段数量
     * @return 知识库检索结果
     */
    @Override
    public List<SearchResult> search(String question, Integer topK) {
        return search(question, topK, null);
    }

    /**
     * 在指定文档范围内检索相似知识片段；文档列表为空时保持原有的全库检索行为
     *
     * @param question 用户问题
     * @param topK 召回片段数量
     * @param documentIds 限定检索的文档标识
     * @return 知识库检索结果
     */
    @Override
    public List<SearchResult> search(String question, Integer topK, List<String> documentIds) {
        int actualTopK = Math.max(1, Math.min(topK, 20));
        List<String> actualDocumentIds = documentIds == null ? new ArrayList<>() : documentIds.stream()
                .filter(documentId -> documentId != null && !documentId.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Embedding queryEmbedding = embeddingService.embed(question);
        List<SearchResult> searchResults;
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            searchResults = elasticsearchService.search(queryEmbedding, actualTopK, actualDocumentIds);
        } else {
            searchResults = knowledgeRepository.listChunks(actualDocumentIds).stream()
                    .map(chunk -> toSearchResult(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
                    // 本地哈希向量对长文本的余弦分数偏低，先按真实关键词保留候选，再进行排序和截取
                    .filter(result -> isRelevantResult(question, result))
                    .sorted(Comparator.comparing(SearchResult::getScore).reversed())
                    .limit(actualTopK)
                    .collect(Collectors.toList());
        }

        return searchResults;
    }

    /**
     * 过滤低相关度结果；本地哈希模式优先使用关键词命中，避免长文本余弦分数误杀真实资料。
     */
    private boolean isRelevantResult(String question, SearchResult result) {
        if (result == null || result.getScore() == null) {
            return false;
        }
        if (!LOCAL_HASH_MODE.equals(embeddingService.getMode())
                || !Boolean.TRUE.equals(localKeywordFilterEnabled)) {
            return result.getScore() >= retrievalMinScore;
        }
        return hasKeywordOverlap(question, result.getTitle() + " " + result.getContent());
    }

    /**
     * 本地哈希向量仅用于演示，不具备可靠语义能力，因此至少要求英文词或中文双字词真实出现
     */
    private boolean hasKeywordOverlap(String question, String content) {
        String normalizedQuestion = normalizeText(question);
        String normalizedContent = normalizeText(content);

        Matcher latinMatcher = LATIN_KEYWORD_PATTERN.matcher(normalizedQuestion);
        while (latinMatcher.find()) {
            String keyword = latinMatcher.group();
            if (keyword.length() > 1 && normalizedContent.contains(keyword)) {
                return true;
            }
        }

        Matcher chineseMatcher = CHINESE_TEXT_PATTERN.matcher(normalizedQuestion);
        while (chineseMatcher.find()) {
            String chineseText = chineseMatcher.group();
            if (chineseText.length() == 1 && normalizedContent.contains(chineseText)) {
                return true;
            }
            for (int index = 0; index < chineseText.length() - 1; index++) {
                String keyword = chineseText.substring(index, index + 2);
                if (!QUERY_STOP_WORDS.contains(keyword) && normalizedContent.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /**
     * 获取向量生成和向量库存储状态
     *
     * @return 系统运行状态
     */
    @Override
    public SystemStatusRespVO getStatus() {
        SystemStatusRespVO result = new SystemStatusRespVO();
        result.setEmbeddingMode(embeddingService.getMode());
        result.setVectorStoreMode(getVectorStoreMode());
        result.setIndexName(elasticsearchIndexName);
        result.setWebSearchEnabled(Boolean.TRUE.equals(webSearchEnabled));
        return result;
    }

    /**
     * 获取当前知识库存储模式
     *
     * @return elasticsearch 或 sqlite
     */
    @Override
    public String getVectorStoreMode() {
        return Boolean.TRUE.equals(elasticsearchEnabled) ? "elasticsearch" : "sqlite";
    }

    /**
     * 完成首次文档切片和索引，索引成功后再保存文档元数据
     */
    private KnowledgeImportRespVO indexDocument(KnowledgeDocument document) {
        List<KnowledgeChunk> chunks = buildChunks(document);
        document.setChunkCount(chunks.size());
        document.setUpdateTime(LocalDateTime.now());
        saveIndex(document, chunks);

        KnowledgeImportRespVO result = new KnowledgeImportRespVO();
        result.setDocumentId(document.getId());
        result.setChunkCount(chunks.size());
        result.setEmbeddingMode(embeddingService.getMode());
        result.setVectorStoreMode(getVectorStoreMode());
        return result;
    }

    /**
     * 根据原文重新生成完整切片列表；全部向量成功生成后才会进入存储替换步骤
     */
    private List<KnowledgeChunk> buildChunks(KnowledgeDocument document) {
        DocumentSplitter documentSplitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = documentSplitter.split(Document.from(document.getContent()));
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            String content = segments.get(index).text();
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setId(document.getId() + "-" + index);
            chunk.setDocumentId(document.getId());
            chunk.setTitle(document.getTitle());
            chunk.setChunkIndex(index);
            chunk.setContent(content);
            chunk.setEmbedding(embeddingService.embed(content));
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 将文档和切片写入当前启用的向量存储
     */
    private void saveIndex(KnowledgeDocument document, List<KnowledgeChunk> chunks) {
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            elasticsearchService.saveAll(chunks);
            knowledgeRepository.saveDocument(document);
            return;
        }
        knowledgeRepository.saveDocumentAndChunks(document, chunks);
    }

    /**
     * 删除指定文档在当前向量存储中的全部切片
     */
    private void deleteChunks(String documentId) {
        if (Boolean.TRUE.equals(elasticsearchEnabled)) {
            elasticsearchService.deleteByDocumentId(documentId);
        }
        knowledgeRepository.deleteChunks(documentId);
    }

    private SearchResult toSearchResult(KnowledgeChunk chunk, double score) {
        SearchResult result = new SearchResult();
        result.setDocumentId(chunk.getDocumentId());
        result.setTitle(chunk.getTitle());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setContent(chunk.getContent());
        result.setScore(score);
        return result;
    }

    /**
     * SQLite 只保存向量 JSON，本地检索时直接计算余弦相似度，适合当前 Demo 的数据规模。
     */
    private double cosineSimilarity(Embedding left, Embedding right) {
        List<Float> leftVector = left.vectorAsList();
        List<Float> rightVector = right.vectorAsList();
        if (leftVector.size() != rightVector.size()) {
            return 0D;
        }

        double dotProduct = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < leftVector.size(); index++) {
            double leftValue = leftVector.get(index);
            double rightValue = rightVector.get(index);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
