package com.eastmoney.agent.controller;

import com.eastmoney.agent.base.RestResponse;
import com.eastmoney.agent.domain.SearchResult;
import com.eastmoney.agent.service.KnowledgeService;
import com.eastmoney.agent.vo.request.KnowledgeImportReqVO;
import com.eastmoney.agent.vo.response.KnowledgeDocumentRespVO;
import com.eastmoney.agent.vo.response.KnowledgeImportRespVO;
import com.eastmoney.agent.vo.response.SystemStatusRespVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;

/**
 * @Author: suyue
 * @name: KnowledgeController
 * @Date: 2026/09/01
 * @Description: 知识库文章导入与检索接口
 */
@Slf4j
@Validated
@RestController
@RequestMapping("api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping("import")
    public RestResponse<?> importDocument(@Valid @RequestBody KnowledgeImportReqVO request) {
        try {
            KnowledgeImportRespVO result = knowledgeService.importDocument(request);
            return RestResponse.success(result);
        } catch (Exception exception) {
            log.error("知识库文章导入异常", exception);
            return RestResponse.exception();
        }
    }

    /**
     * 上传并解析 TXT、Markdown、PDF 或 Word 文档
     */
    @PostMapping("upload")
    public RestResponse<?> upload(@RequestPart("file") MultipartFile file) {
        try {
            return RestResponse.success(knowledgeService.importFile(file));
        } catch (IllegalArgumentException exception) {
            log.warn("知识库文件上传参数异常：{}", exception.getMessage());
            return RestResponse.fail(exception.getMessage());
        } catch (Exception exception) {
            log.error("知识库文件上传异常", exception);
            return RestResponse.exception();
        }
    }

    /**
     * 查询当前已导入的文档
     */
    @GetMapping("documents")
    public RestResponse<?> listDocuments() {
        List<KnowledgeDocumentRespVO> result = knowledgeService.listDocuments();
        return RestResponse.success(result);
    }

    /**
     * 删除文档及其全部知识切片
     */
    @DeleteMapping("documents/{documentId}")
    public RestResponse<?> deleteDocument(@PathVariable String documentId) {
        try {
            knowledgeService.deleteDocument(documentId);
            return RestResponse.success("删除成功");
        } catch (IllegalArgumentException exception) {
            return RestResponse.fail(exception.getMessage());
        } catch (Exception exception) {
            log.error("知识库文档删除异常，documentId={}", documentId, exception);
            return RestResponse.exception();
        }
    }

    /**
     * 使用保存的原始文本重新生成文档切片和向量索引
     */
    @PostMapping("documents/{documentId}/reindex")
    public RestResponse<?> reindexDocument(@PathVariable String documentId) {
        try {
            return RestResponse.success(knowledgeService.reindexDocument(documentId));
        } catch (IllegalArgumentException exception) {
            return RestResponse.fail(exception.getMessage());
        } catch (Exception exception) {
            log.error("知识库文档重新索引异常，documentId={}", documentId, exception);
            return RestResponse.exception();
        }
    }

    @GetMapping("search")
    public RestResponse<?> search(@RequestParam String question,
                                  @RequestParam(defaultValue = "4") Integer topK) {
        try {
            List<SearchResult> result = knowledgeService.search(question, topK);
            return RestResponse.success(result);
        } catch (Exception exception) {
            log.error("知识库检索异常", exception);
            return RestResponse.exception();
        }
    }

    @GetMapping("status")
    public RestResponse<?> status() {
        SystemStatusRespVO result = knowledgeService.getStatus();
        return RestResponse.success(result);
    }
}
