package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.DocumentParserService;
import com.eastmoney.agent.service.ElasticsearchService;
import com.eastmoney.agent.service.EmbeddingService;
import com.eastmoney.agent.vo.request.KnowledgeImportReqVO;
import com.eastmoney.agent.vo.response.KnowledgeImportRespVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: suyue
 * @name: KnowledgeServiceImplTest
 * @Date: 2026/09/01
 * @Description: 知识库文档管理生命周期单元测试
 */
class KnowledgeServiceImplTest {

    private KnowledgeServiceImpl knowledgeService;
    private ElasticsearchService elasticsearchService;

    @BeforeEach
    public void setUp() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        elasticsearchService = mock(ElasticsearchService.class);
        DocumentParserService documentParserService = mock(DocumentParserService.class);
        when(embeddingService.embed(anyString())).thenReturn(Arrays.asList(1D, 0D));
        when(embeddingService.getMode()).thenReturn("test");

        knowledgeService = new KnowledgeServiceImpl();
        ReflectionTestUtils.setField(knowledgeService, "chunkSize", 500);
        ReflectionTestUtils.setField(knowledgeService, "chunkOverlap", 80);
        ReflectionTestUtils.setField(knowledgeService, "elasticsearchEnabled", false);
        ReflectionTestUtils.setField(knowledgeService, "elasticsearchIndexName", "test-index");
        ReflectionTestUtils.setField(knowledgeService, "embeddingService", embeddingService);
        ReflectionTestUtils.setField(knowledgeService, "elasticsearchService", elasticsearchService);
        ReflectionTestUtils.setField(knowledgeService, "documentParserService", documentParserService);
    }

    @Test
    public void shouldImportReindexAndDeleteDocument() {
        KnowledgeImportReqVO request = new KnowledgeImportReqVO();
        request.setTitle("雨天生活知识");
        request.setContent("雨天路滑，出门时应穿防滑鞋并注意慢行。");

        KnowledgeImportRespVO importResult = knowledgeService.importDocument(request);

        assertThat(importResult.getChunkCount()).isEqualTo(1);
        assertThat(knowledgeService.listDocuments()).hasSize(1);
        List<SearchResult> searchResults = knowledgeService.search("雨天出门", 4);
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).getDocumentId()).isEqualTo(importResult.getDocumentId());

        KnowledgeImportRespVO reindexResult = knowledgeService.reindexDocument(importResult.getDocumentId());
        assertThat(reindexResult.getChunkCount()).isEqualTo(1);
        assertThat(knowledgeService.listDocuments().get(0).getUpdateTime()).isNotNull();

        knowledgeService.deleteDocument(importResult.getDocumentId());
        assertThat(knowledgeService.listDocuments()).isEmpty();
        assertThat(knowledgeService.search("雨天出门", 4)).isEmpty();
        verify(elasticsearchService, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    public void shouldRejectMissingDocument() {
        assertThatThrownBy(() -> knowledgeService.reindexDocument("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档不存在");
        assertThatThrownBy(() -> knowledgeService.deleteDocument("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文档不存在");
    }
}
