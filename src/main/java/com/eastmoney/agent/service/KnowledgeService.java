package com.eastmoney.agent.service;

import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.vo.request.KnowledgeImportReqVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentRespVO;
import com.eastmoney.agent.vo.response.KnowledgeImportRespVO;
import com.eastmoney.agent.vo.response.SystemStatusRespVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @Author: suyue
 * @name: KnowledgeService
 * @Date: 2026/09/01
 * @Description: 知识库导入、切分与检索服务
 */
public interface KnowledgeService {

    KnowledgeImportRespVO importDocument(KnowledgeImportReqVO request);

    KnowledgeImportRespVO importFile(MultipartFile file) throws IOException;

    List<SearchResult> search(String question, Integer topK);

    List<KnowledgeDocumentRespVO> listDocuments();

    void deleteDocument(String documentId);

    KnowledgeImportRespVO reindexDocument(String documentId);

    SystemStatusRespVO getStatus();

    String getVectorStoreMode();
}
