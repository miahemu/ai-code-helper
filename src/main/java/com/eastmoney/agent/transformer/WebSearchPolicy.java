package com.eastmoney.agent.transformer;

import com.eastmoney.agent.enums.ChatCommandEnum;

import java.util.regex.Pattern;

/**
 * @Author: suyue
 * @name: WebSearchPolicy
 * @Date: 2026/09/14
 * @Description: 联网搜索触发规则
 */
public final class WebSearchPolicy {

    private static final Pattern REQUIRED_PATTERN = Pattern.compile(
            "(最新|今日|今天|实时|联网(?:搜索|查询|查找)?|网络搜索|网上搜索|"
                    + "近期(?:新闻|动态|消息)|本周(?:新闻|动态|消息)|本月(?:新闻|动态|消息)|"
                    + "latest|today|real[- ]?time|search (?:the )?web|online search)",
            Pattern.CASE_INSENSITIVE);

    private WebSearchPolicy() {
    }

    /**
     * 判断本次问题是否必须联网，显式命令优先于问题中的时效关键词
     *
     * @param question 当前用户问题
     * @return 必须调用联网搜索工具时返回 true
     */
    public static boolean requiresWebSearch(String question) {
        ChatCommandEnum command = ChatCommandEnum.fromQuestion(question);
        if (command == ChatCommandEnum.WEB) {
            return true;
        }
        if (command != null && command != ChatCommandEnum.AUTO) {
            return false;
        }
        return question != null && REQUIRED_PATTERN.matcher(question).find();
    }
}
