package com.yzh666.careerai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * CareerAI Gateway Service.
 * 通过 Nacos 服务发现路由到主应用和知识库服务。
 */
@EnableDiscoveryClient
@SpringBootApplication
public class GatewayServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayServiceApplication.class, args);
  }
}
