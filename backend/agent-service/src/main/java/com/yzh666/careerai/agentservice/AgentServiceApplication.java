package com.yzh666.careerai.agentservice;

import com.yzh666.careerai.common.agent.AgentInternalAccessService;
import com.yzh666.careerai.common.exception.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

@EnableDiscoveryClient
@EnableFeignClients
@Import({AgentInternalAccessService.class, GlobalExceptionHandler.class})
@SpringBootApplication
public class AgentServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentServiceApplication.class, args);
  }
}
