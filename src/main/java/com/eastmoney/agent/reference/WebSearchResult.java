package com.eastmoney.agent.reference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * @Author: suyue
 * @name: WebSearchResult
 * @Date: 2026/09/11
 * @Description: 联网搜索工具单条搜索结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebSearchResult {

    // 网页标题
    private String title;

    // 网页地址
    private String link;
}
