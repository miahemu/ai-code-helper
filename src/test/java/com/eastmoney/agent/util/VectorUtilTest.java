package com.eastmoney.agent.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorUtilTest {

    @Test
    void similarTextShouldHaveHigherScore() {
        List<Double> source = VectorUtil.hashEmbedding("下雨天衣服怎么晾干", 256);
        List<Double> similar = VectorUtil.hashEmbedding("雨天晾衣服的小技巧", 256);
        List<Double> different = VectorUtil.hashEmbedding("周末去公园跑步", 256);

        assertThat(VectorUtil.cosineSimilarity(source, similar))
                .isGreaterThan(VectorUtil.cosineSimilarity(source, different));
    }
}

