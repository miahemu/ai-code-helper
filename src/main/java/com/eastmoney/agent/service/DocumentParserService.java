package com.eastmoney.agent.service;

import com.eastmoney.agent.domain.DocumentParseResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @Author: suyue
 * @name: DocumentParserService
 * @Date: 2026/09/01
 * @Description: 知识库上传文档解析服务
 */
public interface DocumentParserService {

    /**
     * 根据文件扩展名解析文档正文。
     *
     * @param file 上传文件
     * @return 文档解析结果
     * @throws IOException 文件读取或解析失败
     */
    DocumentParseResult parse(MultipartFile file) throws IOException;
}
