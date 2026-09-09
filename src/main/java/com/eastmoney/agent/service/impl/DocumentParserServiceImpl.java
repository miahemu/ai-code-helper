package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.DocumentParseResult;
import com.eastmoney.agent.enums.DocumentTypeEnum;
import com.eastmoney.agent.service.DocumentParserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @Author: suyue
 * @name: DocumentParserServiceImpl
 * @Date: 2026/09/01
 * @Description: TXT、Markdown、PDF 和 Word 文档解析实现
 */
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    /**
     * 根据文件扩展名解析文档正文
     *
     * @param file 上传文件
     * @return 文档解析结果
     * @throws IOException 文件读取或解析失败
     */
    @Override
    public DocumentParseResult parse(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = StringUtils.getFilename(StringUtils.cleanPath(file.getOriginalFilename()));
        DocumentTypeEnum documentType = DocumentTypeEnum.fromFilename(filename);
        String content;
        switch (documentType) {
            case TXT:
            case MARKDOWN:
                try (InputStream inputStream = file.getInputStream()) {
                    content = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                }
                break;
            case PDF:
                content = parsePdf(file);
                break;
            case DOC:
                content = parseDoc(file);
                break;
            case DOCX:
                content = parseDocx(file);
                break;
            default:
                throw new IllegalArgumentException("不支持的文件类型");
        }
        content = content == null ? "" : content.replace('\u0000', ' ').trim();
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文档中未解析到有效文本");
        }

        DocumentParseResult result = new DocumentParseResult();
        result.setFilename(filename);
        result.setTitle(filename.substring(0, filename.lastIndexOf('.')));
        result.setDocumentType(documentType);
        result.setContent(content);
        return result;
    }

    private String parsePdf(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = PDDocument.load(inputStream)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String parseDoc(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parseDocx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
