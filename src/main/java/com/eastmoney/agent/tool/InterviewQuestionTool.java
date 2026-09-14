package com.eastmoney.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: suyue
 * @name: InterviewQuestionTool
 * @Date: 2026/09/10
 * @Description: 提供给 LangChain4j Agent 的面试题搜索工具
 */
@Slf4j
@Component
public class InterviewQuestionTool {

    private static final String SEARCH_URL = "https://www.mianshiya.com/search/all?searchText=";

    /**
     * 从面试鸭网站获取关键词相关的面试题列表
     *
     * @param keyword 搜索关键词（如“redis”、“java 多线程”）
     * @return 面试题列表，若失败则返回错误信息
     */
    @Tool(name = AgentToolConstants.INTERVIEW_SEARCH, value = "根据关键词从面试鸭网站搜索相关面试题。"
            + "当用户需要特定技术、编程概念或岗位相关的面试题时调用，参数应为清晰的搜索关键词。")
    public String searchInterviewQuestions(
            @P(name = "keyword", value = "面试题搜索关键词") String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "面试题搜索关键词不能为空";
        }

        String encodedKeyword = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        String url = SEARCH_URL + encodedKeyword;
        Document document;
        try {
            document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(5000)
                    .get();
        } catch (IOException exception) {
            log.error("搜索面试题异常，keyword={}", keyword, exception);
            return "搜索面试题失败：" + exception.getMessage();
        }

        List<String> questions = new ArrayList<>();
        Elements questionElements = document.select(".ant-table-cell > a");
        questionElements.forEach(element -> {
            String question = element.text().trim();
            if (!question.isEmpty()) {
                questions.add(question);
            }
        });
        return questions.isEmpty() ? "未搜索到相关面试题" : String.join("\n", questions);
    }
}
