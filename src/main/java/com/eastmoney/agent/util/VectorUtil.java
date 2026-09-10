package com.eastmoney.agent.util;

import dev.langchain4j.data.embedding.Embedding;

import java.util.Locale;

/**
 * @Author: suyue
 * @name: VectorUtil
 * @Date: 2026/09/01
 * @Description: 本地哈希向量生成工具
 */
public final class VectorUtil {

    private VectorUtil() {
    }

    public static Embedding hashEmbedding(String text, int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("向量维度必须大于 0");
        }
        double[] values = new double[dimensions];
        String normalizedText = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        for (int index = 0; index < normalizedText.length(); index++) {
            char current = normalizedText.charAt(index);
            if (!Character.isWhitespace(current)) {
                addFeature(values, String.valueOf(current));
            }
            if (index + 1 < normalizedText.length()) {
                String bigram = normalizedText.substring(index, index + 2);
                if (!bigram.trim().isEmpty()) {
                    addFeature(values, bigram);
                }
            }
        }

        double norm = 0D;
        for (double value : values) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        float[] result = new float[dimensions];
        for (int index = 0; index < values.length; index++) {
            result[index] = (float) (norm == 0D ? 0D : values[index] / norm);
        }
        return Embedding.from(result);
    }

    private static void addFeature(double[] values, String feature) {
        int hash = feature.hashCode();
        int position = (hash & Integer.MAX_VALUE) % values.length;
        values[position] += (hash & 1) == 0 ? 1D : -1D;
    }

}
