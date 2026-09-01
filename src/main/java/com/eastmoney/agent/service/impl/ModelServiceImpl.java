package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.ModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author: suyue
 * @name: ModelServiceImpl
 * @Date: 2026/09/01
 * @Description: OpenAI 兼容大模型调用实现
 */
@Service
public class ModelServiceImpl implements ModelService {

    @Value("${ai.chat.url}")
    private String chatUrl;

    @Value("${ai.chat.api-key}")
    private String chatApiKey;

    @Value("${ai.chat.model}")
    private String chatModel;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 根据用户问题和知识库检索结果生成回答。
     *
     * @param question 用户问题
     * @param references 知识库检索结果
     * @return 模型回答
     */
    @Override
    public String chat(String question, List<SearchResult> references) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(chatApiKey);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildSystemPrompt(references));
        messages.add(systemMessage);

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", question);
        messages.add(userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", chatModel);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3D);

        Map response = restTemplate.postForObject(chatUrl, new HttpEntity<>(requestBody, headers), Map.class);
        return parseAnswer(response);
    }

    /**
     * 将召回的知识片段组装为模型系统提示词。
     *
     * @param references 知识库检索结果
     * @return 系统提示词
     */
    private String buildSystemPrompt(List<SearchResult> references) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个贴近日常生活的中文助手。请优先依据下面的知识库片段回答；")
                .append("知识库没有答案时，要明确说明，并给出简洁、实用的通用建议。不要编造知识库中不存在的事实。\n\n");
        for (int index = 0; index < references.size(); index++) {
            SearchResult reference = references.get(index);
            prompt.append("[资料").append(index + 1).append("] 标题：")
                    .append(reference.getTitle()).append("\n")
                    .append(reference.getContent()).append("\n\n");
        }
        return prompt.toString();
    }

    /**
     * 从 OpenAI Chat Completions 响应中解析回答内容。
     *
     * @param response 模型接口响应
     * @return 回答内容
     */
    private String parseAnswer(Map response) {
        if (response == null || !(response.get("choices") instanceof List)) {
            throw new IllegalStateException("模型接口返回格式不正确：缺少 choices");
        }
        List choices = (List) response.get("choices");
        if (choices.isEmpty() || !(choices.get(0) instanceof Map)) {
            throw new IllegalStateException("模型接口返回格式不正确：choices 为空");
        }
        Object message = ((Map) choices.get(0)).get("message");
        if (!(message instanceof Map) || ((Map) message).get("content") == null) {
            throw new IllegalStateException("模型接口返回格式不正确：缺少 message.content");
        }
        return String.valueOf(((Map) message).get("content"));
    }
}
