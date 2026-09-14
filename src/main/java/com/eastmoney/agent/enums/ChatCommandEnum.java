package com.eastmoney.agent.enums;

import lombok.Getter;

import java.util.Locale;

/**
 * @Author: suyue
 * @name: ChatCommandEnum
 * @Date: 2026/09/14
 * @Description: 服务端对答命令及解析结果
 */
@Getter
public enum ChatCommandEnum {

    AUTO("/auto"),
    KNOWLEDGE("/kb"),
    WEB("/web"),
    INTERVIEW("/interview"),
    UNKNOWN(null);

    private final String command;

    ChatCommandEnum(String command) {
        this.command = command;
    }

    /**
     * 解析问题开头的斜杠命令，普通问题返回 null，无法识别的命令返回 UNKNOWN
     *
     * @param question 当前用户问题
     * @return 对答命令解析结果
     */
    public static ChatCommandEnum fromQuestion(String question) {
        String commandName = parseCommandName(question);
        if (commandName == null) {
            return null;
        }
        for (ChatCommandEnum commandEnum : values()) {
            if (commandEnum.command != null && commandEnum.command.equals(commandName)) {
                return commandEnum;
            }
        }
        return UNKNOWN;
    }

    /**
     * 提取问题开头的命令名称，支持空格、换行及制表符分隔命令正文
     *
     * @param question 当前用户问题
     * @return 命令名称，普通问题返回 null
     */
    private static String parseCommandName(String question) {
        if (question == null) {
            return null;
        }
        String value = question.trim();
        if (!value.startsWith("/")) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return value.substring(0, index).toLowerCase(Locale.ROOT);
            }
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
