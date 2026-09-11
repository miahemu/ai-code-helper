package com.eastmoney.agent.vo.response;

import lombok.Data;

/**
 * @Author: suyue
 * @name: RelatedUrlRespVO
 * @Date: 2026/09/11
 * @Description: 联网搜索相关网页
 */
@Data
public class RelatedUrlRespVO {

    // 网页标题
    private String title;

    // 网页地址
    private String url;
}
