package com.yzh666.careerai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CareerAI Gateway Service.
 * 第一阶段使用静态路由转发到主应用和知识库服务。
 */
@SpringBootApplication
public class GatewayServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(GatewayServiceApplication.class, args);
  }
}
