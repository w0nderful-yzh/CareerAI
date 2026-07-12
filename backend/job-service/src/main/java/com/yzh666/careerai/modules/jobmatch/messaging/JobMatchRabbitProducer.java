package com.yzh666.careerai.modules.jobmatch.messaging;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.infrastructure.rabbitmq.CareerRabbitProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobMatchRabbitProducer {

  private final RabbitTemplate rabbitTemplate;
  private final CareerRabbitProperties rabbitProperties;
  private final ObjectMapper objectMapper;

  public void send(JobMatchTaskMessage message) {
    sendToExchange(
        rabbitProperties.getJobMatch().getExchange(),
        rabbitProperties.getJobMatch().getRoutingKey(),
        message
    );
  }

  public void sendRetry(JobMatchTaskMessage message) {
    sendToExchange(
        rabbitProperties.getJobMatch().getRetryExchange(),
        rabbitProperties.getJobMatch().getRetryRoutingKey(),
        message
    );
  }

  private void sendToExchange(String exchange, String routingKey, JobMatchTaskMessage message) {
    try {
      String payload = objectMapper.writeValueAsString(message);
      rabbitTemplate.convertAndSend(exchange, routingKey, payload);
      log.info(
          "已投递岗位匹配任务: exchange={}, routingKey={}, taskId={}, attempt={}",
          exchange,
          routingKey,
          message.taskId(),
          message.safeAttempt()
      );
    } catch (JacksonException e) {
      throw new BusinessException(ErrorCode.JOB_MATCH_FAILED, "岗位匹配任务序列化失败");
    }
  }
}
