package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.service.ModelService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: suyue
 * @name: ChatServiceImplTest
 * @Date: 2026/09/09
 * @Description: 固定 RAG 对答编排回归测试
 */
class ChatServiceImplTest {

    private ChatServiceImpl chatService;
    private KnowledgeService knowledgeService;
    private ModelService modelService;

    @BeforeEach
    public void setUp() {
        knowledgeService = mock(KnowledgeService.class);
        modelService = mock(ModelService.class);
        chatService = new ChatServiceImpl();
        ReflectionTestUtils.setField(chatService, "knowledgeService", knowledgeService);
        ReflectionTestUtils.setField(chatService, "modelService", modelService);
    }

    @Test
    public void shouldSearchKnowledgeAndReturnModelAnswer() {
        ChatReqVO request = new ChatReqVO();
        request.setQuestion("下雨天衣服不干怎么办？");
        request.setTopK(3);

        SearchResult searchResult = new SearchResult();
        searchResult.setDocumentId("document-1");
        searchResult.setTitle("雨天生活知识");
        searchResult.setChunkIndex(0);
        searchResult.setContent("可以使用风扇或空调除湿模式加快衣服干燥。");
        searchResult.setScore(0.95D);
        List<SearchResult> searchResults = Collections.singletonList(searchResult);

        when(knowledgeService.search(request.getQuestion(), request.getTopK())).thenReturn(searchResults);
        when(knowledgeService.getVectorStoreMode()).thenReturn("memory");
        when(modelService.chat(request.getQuestion(), searchResults)).thenReturn("可以使用风扇辅助通风。");

        ChatRespVO result = chatService.chat(request);

        assertThat(result.getAnswer()).isEqualTo("可以使用风扇辅助通风。");
        assertThat(result.getVectorStoreMode()).isEqualTo("memory");
        assertThat(result.getReferences()).hasSize(1);
        assertThat(result.getReferences().get(0).getDocumentId()).isEqualTo("document-1");
        assertThat(result.getReferences().get(0).getTitle()).isEqualTo("雨天生活知识");
        assertThat(result.getReferences().get(0).getChunkIndex()).isZero();
        assertThat(result.getReferences().get(0).getContent())
                .isEqualTo("可以使用风扇或空调除湿模式加快衣服干燥。");
        assertThat(result.getReferences().get(0).getScore()).isEqualTo(0.95D);
        verify(knowledgeService).search(request.getQuestion(), request.getTopK());
        verify(modelService).chat(eq(request.getQuestion()), same(searchResults));
    }
}
