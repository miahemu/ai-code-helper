package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @Author: suyue
 * @name: ModelServiceImplTest
 * @Date: 2026/09/09
 * @Description: OpenAI 兼容模型调用回归测试
 */
class ModelServiceImplTest {

    private static final String CHAT_URL = "http://localhost/v1/chat/completions";

    private ModelServiceImpl modelService;
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        restTemplate = mock(RestTemplate.class);
        modelService = new ModelServiceImpl();
        ReflectionTestUtils.setField(modelService, "chatUrl", CHAT_URL);
        ReflectionTestUtils.setField(modelService, "chatApiKey", "test-key");
        ReflectionTestUtils.setField(modelService, "chatModel", "test-model");
        ReflectionTestUtils.setField(modelService, "restTemplate", restTemplate);
    }

    @Test
    public void shouldBuildRagMessagesAndParseAnswer() {
        SearchResult reference = new SearchResult();
        reference.setTitle("雨天生活知识");
        reference.setContent("下雨天可以使用风扇加快衣服干燥。");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("content", "使用风扇加强空气流通。");
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("choices", Collections.singletonList(choice));
        when(restTemplate.postForObject(eq(CHAT_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);

        String answer = modelService.chat("下雨天衣服不干怎么办？", Collections.singletonList(reference));

        assertThat(answer).isEqualTo("使用风扇加强空气流通。");
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(eq(CHAT_URL), requestCaptor.capture(), eq(Map.class));
        assertThat(requestCaptor.getValue().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer test-key");

        Map requestBody = (Map) requestCaptor.getValue().getBody();
        assertThat(requestBody.get("model")).isEqualTo("test-model");
        assertThat(requestBody.get("temperature")).isEqualTo(0.3D);
        List<Map<String, String>> messages = (List<Map<String, String>>) requestBody.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content"))
                .contains("雨天生活知识", "下雨天可以使用风扇加快衣服干燥。");
        assertThat(messages.get(1).get("role")).isEqualTo("user");
        assertThat(messages.get(1).get("content")).isEqualTo("下雨天衣服不干怎么办？");
    }

    @Test
    public void shouldRejectInvalidModelResponse() {
        when(restTemplate.postForObject(eq(CHAT_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Collections.emptyMap());

        assertThatThrownBy(() -> modelService.chat("测试问题", Collections.emptyList()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少 choices");
    }
}
