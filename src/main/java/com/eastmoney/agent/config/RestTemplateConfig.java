package com.eastmoney.agent.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * @Author: suyue
 * @name: RestTemplateConfig
 * @Date: 2026/09/01
 * @Description: 外部模型与 Elasticsearch HTTP 客户端配置
 */
@Configuration
public class RestTemplateConfig {

    @Value("${http.connect-timeout}")
    private Integer connectTimeout;

    @Value("${http.read-timeout}")
    private Integer readTimeout;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Bean
    public RestTemplate restTemplate() {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeout))
                .setReadTimeout(Duration.ofMillis(readTimeout))
                .build();
    }
}
