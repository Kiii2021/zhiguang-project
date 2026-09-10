package com.tongji.config;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.RequiredArgsConstructor;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;


// 这个类是Elasticsearch配置类，提供客户端登录加载的方法

@Configuration  // 标识为 Spring 配置类
@EnableConfigurationProperties(EsProperties.class)  // 启用配置属性绑定
@RequiredArgsConstructor  // Lombok：生成包含 final 字段的构造器
public class ElasticsearchConfig {
    private final EsProperties esProperties;  // 承载配置文件中的属性

    @Bean // 将返回对象注册为 Spring Bean
    public ElasticsearchClient elasticsearchClient() {  // 创建支持认证的 Elasticsearch 客户端
        BasicCredentialsProvider creds = new BasicCredentialsProvider();

        if(StringUtils.hasText(esProperties.getUsername())){ // 如果有配置用户名密码，则启用认证
            creds.setCredentials(AuthScope.ANY,  // 认证范围：所有
                    new UsernamePasswordCredentials(esProperties.getUsername(), esProperties.getPassword()));
        }
        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(esProperties.getHost()))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setDefaultCredentialsProvider(creds));  // 配置HTTP连接
        RestClient restClient = builder.build();  // 构建底层 REST 客户端
        RestClientTransport transport = new RestClientTransport(restClient,
                new JacksonJsonpMapper()); // JSON 序列化器

        return new ElasticsearchClient(transport);  // 返回 Elasticsearch 客户端
    }
}
