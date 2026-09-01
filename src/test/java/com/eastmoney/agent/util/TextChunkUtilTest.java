package com.eastmoney.agent.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkUtilTest {

    @Test
    public void shouldSplitTextWithOverlap() {
        List<String> chunks = TextChunkUtil.chunk("第一段生活知识。第二段生活知识。第三段生活知识。", 12, 3);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0)).isNotBlank();
        assertThat(chunks.get(1)).isNotBlank();
    }
}

