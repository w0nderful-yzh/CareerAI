package com.yzh666.careerai.modules.jobmatch.messaging;

import com.rabbitmq.client.Channel;
import com.yzh666.careerai.common.model.AsyncTaskStatus;
import com.yzh666.careerai.infrastructure.rabbitmq.CareerRabbitProperties;
import com.yzh666.careerai.modules.aitask.model.AiAnalysisTaskEntity;
import com.yzh666.careerai.modules.aitask.repository.AiAnalysisTaskRepository;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobMatchRabbitListener {

  private final AiAnalysisTaskRepository taskRepository;
  private final JobMatchService jobMatchService;
  private final JobMatchRabbitProducer producer;
  private final CareerRabbitProperties rabbitProperties;
  private final ObjectMapper objectMapper;

  @RabbitListener(
      queues = "${app.rabbitmq.job-match.queue:careerai.ai.job-match}",
      containerFactory = "manualAckRabbitListenerContainerFactory"
  )
  public void handle(Message message, Channel channel) throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    JobMatchTaskMessage taskMessage = readMessage(message);
    if (taskMessage == null) {
      channel.basicReject(deliveryTag, false);
      return;
    }

    try {
      process(taskMessage);
      // 业务报告和任务状态都保存成功后才确认消息，避免“消息已确认但结果未落库”。
      channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
      handleFailure(taskMessage, deliveryTag, channel, e);
    }
  }

  private void process(JobMatchTaskMessage message) {
    AiAnalysisTaskEntity task = taskRepository.findById(message.taskId()).orElse(null);
    if (task == null) {
      log.warn("岗位匹配任务不存在，忽略消息: taskId={}", message.taskId());
      return;
    }
    if (task.getStatus() == AsyncTaskStatus.COMPLETED) {
      // RabbitMQ 可能重复投递，完成态任务不再次调用模型生成第二份报告。
      log.info("岗位匹配任务已完成，忽略重复消息: taskId={}", message.taskId());
      return;
    }

    task.setStatus(AsyncTaskStatus.PROCESSING);
    task.setRetryCount(message.safeAttempt());
    task.setErrorMessage(null);
    taskRepository.save(task);

    JobMatchReportDTO report = jobMatchService.createReportForUser(
        message.userId(),
        message.resumeId(),
        message.jobId()
    );

    task.setStatus(AsyncTaskStatus.COMPLETED);
    task.setResultId(report.id());
    task.setErrorMessage(null);
    taskRepository.save(task);
    log.info("岗位匹配异步任务完成: taskId={}, reportId={}", task.getId(), report.id());
  }

  private void handleFailure(
      JobMatchTaskMessage message,
      long deliveryTag,
      Channel channel,
      Exception failure
  ) throws IOException {
    int nextAttempt = message.safeAttempt() + 1;
    int maxRetries = rabbitProperties.getJobMatch().getMaxRetries();
    String errorMessage = truncate(failure.getMessage());

    AiAnalysisTaskEntity task = taskRepository.findById(message.taskId()).orElse(null);
    if (task != null) {
      task.setRetryCount(nextAttempt);
      task.setErrorMessage(errorMessage);
      task.setStatus(nextAttempt <= maxRetries ? AsyncTaskStatus.PENDING : AsyncTaskStatus.FAILED);
      taskRepository.save(task);
    }

    if (nextAttempt <= maxRetries) {
      // 先发布带新 attempt 的重试消息，再确认当前消息；超过上限后 reject 进入 DLQ。
      producer.sendRetry(new JobMatchTaskMessage(
          message.taskId(),
          message.userId(),
          message.resumeId(),
          message.jobId(),
          nextAttempt
      ));
      channel.basicAck(deliveryTag, false);
      log.warn(
          "岗位匹配任务失败，已进入重试队列: taskId={}, attempt={}/{}, error={}",
          message.taskId(),
          nextAttempt,
          maxRetries,
          errorMessage
      );
      return;
    }

    channel.basicReject(deliveryTag, false);
    log.error(
        "岗位匹配任务失败并进入死信队列: taskId={}, attempts={}, error={}",
        message.taskId(),
        nextAttempt,
        errorMessage,
        failure
    );
  }

  private JobMatchTaskMessage readMessage(Message message) {
    try {
      String payload = new String(message.getBody(), StandardCharsets.UTF_8);
      return objectMapper.readValue(payload, JobMatchTaskMessage.class);
    } catch (Exception e) {
      log.error("解析岗位匹配 RabbitMQ 消息失败: {}", e.getMessage(), e);
      return null;
    }
  }

  private String truncate(String message) {
    if (message == null || message.isBlank()) {
      return "岗位匹配任务执行失败";
    }
    return message.length() <= 900 ? message : message.substring(0, 900);
  }
}
