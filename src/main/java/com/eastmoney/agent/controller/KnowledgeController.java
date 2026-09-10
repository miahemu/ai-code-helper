package com.eastmoney.agent.controller;

import com.eastmoney.agent.base.RestResponse;
import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.vo.request.KnowledgeImportReqVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentDetailRespVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentRespVO;
import com.eastmoney.agent.vo.response.KnowledgeImportRespVO;
import com.eastmoney.agent.vo.response.SystemStatusRespVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

/**
 * @Author: suyue
 * @name: KnowledgeController
 * @Date: 2026/09/01
 * @Description: 知识库文档管理、知识检索与运行状态接口
 */
@Slf4j
@Validated
@RestController
@RequestMapping("api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * 上传自定义标题和文本内容，创建知识库文档
     */
    @PostMapping("documents/text")
    public RestResponse<?> uploadTextDocument(@Valid @RequestBody KnowledgeImportReqVO request) {
        try {
            KnowledgeImportRespVO result = knowledgeService.importDocument(request);
            return RestResponse.success(result);
        } catch (Exception exception) {
            log.error("知识库文本文件上传异常", exception);
            return RestResponse.exception();
        }
    }

    /**
     * 上传并解析 TXT、Markdown、PDF 或 Word 文件，创建知识库文档
     */
    @PostMapping("documents/file")
    public RestResponse<?> uploadFileDocument(@RequestPart("file") MultipartFile file) {
        try {
            return RestResponse.success(knowledgeService.importFile(file));
        } catch (Exception exception) {
            log.error("知识库文件上传异常", exception);
            return RestResponse.exception();
        }
    }

    /**
     * 查询当前已导入的知识库文档列表
     */
    @GetMapping("documents")
    public RestResponse<?> listDocuments() {
        List<KnowledgeDocumentRespVO> result = knowledgeService.listDocuments();
        return RestResponse.success(result);
    }

    /**
     * 查询指定知识库文档的原始内容
     */
    @GetMapping("documents/{documentId}")
    public RestResponse<?> getDocument(@PathVariable String documentId) {
        try {
            KnowledgeDocumentDetailRespVO result = knowledgeService.getDocument(documentId);
            return RestResponse.success(result);
        } catch (Exception exception) {
            log.error("知识库文档详情查询异常，documentId={}", documentId, exception);
            return RestResponse.exception();
        }
    }

    /**
     * 删除指定知识库文档及其全部知识切片
     */
    @PostMapping("documents/{documentId}/delete")
    public RestResponse<?> deleteDocument(@PathVariable String documentId) {
        try {
            knowledgeService.deleteDocument(documentId);
            return RestResponse.success("删除成功");
        } catch (Exception exception) {
            log.error("知识库文档删除异常，documentId={}", documentId, exception);
            return RestResponse.exception();
        }
    }

    /**
     * 使用保存的原始文本重新生成指定文档的切片和向量索引
     */
    @PostMapping("documents/{documentId}/reindex")
    public RestResponse<?> reindexDocument(@PathVariable String documentId) {
        try {
            KnowledgeImportRespVO respVO = knowledgeService.reindexDocument(documentId);
            return RestResponse.success(respVO);
        } catch (Exception exception) {
            log.error("知识库文档重新索引异常，documentId={}", documentId, exception);
            return RestResponse.exception();
        }
    }

    /**
     * 根据问题检索相关知识切片
     */
    @GetMapping("chunks/search")
    public RestResponse<?> searchKnowledgeChunks(@RequestParam String question,
                                                 @RequestParam(defaultValue = "4") Integer topK) {
        try {
            List<SearchResult> result = knowledgeService.search(question, topK);
            return RestResponse.success(result);
        } catch (Exception exception) {
            log.error("知识库检索异常", exception);
            return RestResponse.exception();
        }
    }

    /**
     * 获取知识库向量生成和存储运行状态
     */
    @GetMapping("status")
    public RestResponse<?> getKnowledgeStatus() {
        SystemStatusRespVO result = knowledgeService.getStatus();
        return RestResponse.success(result);
    }
}
