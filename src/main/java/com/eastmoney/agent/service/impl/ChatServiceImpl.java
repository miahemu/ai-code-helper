package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.ChatService;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.service.ModelService;
import com.eastmoney.agent.vo.request.ChatReqVO;
import com.eastmoney.agent.vo.response.ChatRespVO;
import com.eastmoney.agent.vo.response.KnowledgeReferenceRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: suyue
 * @name: ChatServiceImpl
 * @Date: 2026/09/01
 * @Description: 知识检索与模型回答编排实现
 */
@Service
public class ChatServiceImpl implements ChatService {

    /** 知识库检索服务 */
    @Autowired
    private KnowledgeService knowledgeService;

    /** 大模型调用服务 */
    @Autowired
    private ModelService modelService;

    /**
     * 检索与用户问题相关的知识片段，并调用模型生成最终回答。
     *
     * @param request AI 对答请求参数
     * @return AI 对答结果
     */
    @Override
    public ChatRespVO chat(ChatReqVO request) {
        List<SearchResult> searchResults = knowledgeService.search(request.getQuestion(), request.getTopK());
        List<KnowledgeReferenceRespVO> references = new ArrayList<>();
        for (SearchResult searchResult : searchResults) {
            KnowledgeReferenceRespVO reference = new KnowledgeReferenceRespVO();
            BeanUtils.copyProperties(searchResult, reference);
            references.add(reference);
        }

        ChatRespVO result = new ChatRespVO();
        result.setAnswer(modelService.chat(request.getQuestion(), searchResults));
        result.setVectorStoreMode(knowledgeService.getVectorStoreMode());
        result.setReferences(references);
        return result;
    }
}
