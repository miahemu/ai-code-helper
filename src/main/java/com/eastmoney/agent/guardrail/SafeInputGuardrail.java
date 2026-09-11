package com.eastmoney.agent.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @Author: suyue
 * @name: SafeInputGuardrail
 * @Date: 2026/09/11
 * @Description: 用户输入安全检测护轨
 */
public class SafeInputGuardrail implements InputGuardrail {

    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+"
                    + "(instructions?|prompts?|rules?)"),
            Pattern.compile("(?i)(reveal|show|print|repeat|output).{0,30}(system|developer)\\s*"
                    + "(prompt|message|instructions?)"),
            Pattern.compile("(忽略|无视|绕过|覆盖).{0,16}(之前|此前|以上|系统|开发者).{0,12}(指令|提示词|规则)"),
            Pattern.compile("(输出|泄露|显示|重复).{0,16}(系统提示词|开发者消息|隐藏指令)")
    );

    private static final List<Pattern> CREDENTIAL_PATTERNS = List.of(
            Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._-]{20,}\\b"),
            Pattern.compile("(?i)\\b(?:sk|ak)-[A-Za-z0-9_-]{16,}\\b")
    );

    private static final List<Pattern> HIGH_RISK_REQUEST_PATTERNS = List.of(
            Pattern.compile("(教我|帮我|告诉我如何)(?!防止|避免|检测|识别|应对|阻止).{0,12}"
                    + "(制作炸弹|制造爆炸物|窃取密码|编写勒索软件|实施网络攻击)"),
            Pattern.compile("(?i)(teach me|help me|tell me how to)(?!\\s+(prevent|avoid|detect|identify|stop))"
                    + ".{0,20}(build a bomb|steal passwords?|write ransomware|launch a cyber ?attack)")
    );

    /**
     * 在请求发送给模型前检查提示词注入、凭证泄露和高风险操作请求
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String inputText = userMessage.singleText();
        if (matches(inputText, PROMPT_INJECTION_PATTERNS)) {
            return fatal("检测到可能的提示词注入，请调整问题后重试");
        }
        if (matches(inputText, CREDENTIAL_PATTERNS)) {
            return fatal("检测到敏感凭证，请移除后重试");
        }
        if (matches(inputText, HIGH_RISK_REQUEST_PATTERNS)) {
            return fatal("该请求包含高风险操作指导，无法处理");
        }
        return success();
    }

    private boolean matches(String content, List<Pattern> patterns) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return patterns.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }
}
