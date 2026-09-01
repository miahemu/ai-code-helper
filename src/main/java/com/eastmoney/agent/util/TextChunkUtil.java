package com.eastmoney.agent.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @Author: suyue
 * @name: TextChunkUtil
 * @Date: 2026/09/01
 * @Description: 文档定长重叠切分工具
 */
public final class TextChunkUtil {

    private TextChunkUtil() {
    }

    public static List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("切片大小必须大于 0，重叠长度必须小于切片大小");
        }

        String normalizedText = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalizedText.length()) {
            int end = Math.min(start + chunkSize, normalizedText.length());
            if (end < normalizedText.length()) {
                int boundary = findBoundary(normalizedText, start, end, chunkSize / 2);
                if (boundary > start) {
                    end = boundary;
                }
            }

            String chunk = normalizedText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= normalizedText.length()) {
                break;
            }
            int nextStart = end - overlap;
            start = nextStart > start ? nextStart : end;
        }
        return chunks;
    }

    private static int findBoundary(String text, int start, int end, int searchRange) {
        int lowerBound = Math.max(start + 1, end - searchRange);
        String boundaries = "\n。！？!?；;";
        for (int index = end - 1; index >= lowerBound; index--) {
            if (boundaries.indexOf(text.charAt(index)) >= 0) {
                return index + 1;
            }
        }
        return end;
    }
}

