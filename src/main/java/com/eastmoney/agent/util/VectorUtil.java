package com.eastmoney.agent.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @Author: suyue
 * @name: VectorUtil
 * @Date: 2026/09/01
 * @Description: 本地哈希向量及余弦相似度工具
 */
public final class VectorUtil {

    private VectorUtil() {
    }

    public static List<Double> hashEmbedding(String text, int dimensions) {
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
        List<Double> result = new ArrayList<>(dimensions);
        for (double value : values) {
            result.add(norm == 0D ? 0D : value / norm);
        }
        return result;
    }

    private static void addFeature(double[] values, String feature) {
        int hash = feature.hashCode();
        int position = (hash & Integer.MAX_VALUE) % values.length;
        values[position] += (hash & 1) == 0 ? 1D : -1D;
    }

    public static double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0D || rightNorm == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}

