package com.eastmoney.agent.service.impl;

import com.eastmoney.agent.domain.DocumentParseResult;
import com.eastmoney.agent.enums.DocumentTypeEnum;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @Author: suyue
 * @name: DocumentParserServiceImplTest
 * @Date: 2026/09/01
 * @Description: 文档解析服务单元测试
 */
class DocumentParserServiceImplTest {

    private final DocumentParserServiceImpl documentParserService = new DocumentParserServiceImpl();

    @Test
    public void shouldParseTextFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "生活常识.txt", "text/plain", "雨天注意防滑".getBytes(StandardCharsets.UTF_8));

        DocumentParseResult result = documentParserService.parse(file);

        assertThat(result.getTitle()).isEqualTo("生活常识");
        assertThat(result.getDocumentType()).isEqualTo(DocumentTypeEnum.TXT);
        assertThat(result.getContent()).isEqualTo("雨天注意防滑");
    }

    @Test
    public void shouldParsePdfFile() throws Exception {
        byte[] content;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("PDF knowledge content");
                contentStream.endText();
            }
            document.save(outputStream);
            content = outputStream.toByteArray();
        }

        DocumentParseResult result = documentParserService.parse(
                new MockMultipartFile("file", "knowledge.pdf", "application/pdf", content));

        assertThat(result.getDocumentType()).isEqualTo(DocumentTypeEnum.PDF);
        assertThat(result.getContent()).contains("PDF knowledge content");
    }

    @Test
    public void shouldParseDocxFile() throws Exception {
        byte[] content;
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Word knowledge content");
            document.write(outputStream);
            content = outputStream.toByteArray();
        }

        DocumentParseResult result = documentParserService.parse(
                new MockMultipartFile("file", "knowledge.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content));

        assertThat(result.getDocumentType()).isEqualTo(DocumentTypeEnum.DOCX);
        assertThat(result.getContent()).contains("Word knowledge content");
    }

    @Test
    public void shouldRejectUnsupportedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "knowledge.xlsx", "application/octet-stream", new byte[]{1});

        assertThatThrownBy(() -> documentParserService.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持");
    }
}
