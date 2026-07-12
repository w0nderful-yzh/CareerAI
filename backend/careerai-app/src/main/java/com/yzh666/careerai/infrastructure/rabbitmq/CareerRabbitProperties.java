package com.yzh666.careerai.infrastructure.rabbitmq;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rabbitmq")
public class CareerRabbitProperties {

  private boolean enabled = true;
  private JobMatch jobMatch = new JobMatch();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public JobMatch getJobMatch() {
    return jobMatch;
  }

  public void setJobMatch(JobMatch jobMatch) {
    this.jobMatch = jobMatch;
  }

  public static class JobMatch {
    private String exchange = "careerai.ai";
    private String retryExchange = "careerai.ai.retry";
    private String deadLetterExchange = "careerai.ai.dlx";
    private String queue = "careerai.ai.job-match";
    private String retryQueue = "careerai.ai.job-match.retry";
    private String deadLetterQueue = "careerai.ai.job-match.dlq";
    private String routingKey = "job-match";
    private String retryRoutingKey = "job-match.retry";
    private String deadLetterRoutingKey = "job-match.dead";
    private long retryDelayMs = 10000;
    private int maxRetries = 3;

    public String getExchange() {
      return exchange;
    }

    public void setExchange(String exchange) {
      this.exchange = exchange;
    }

    public String getRetryExchange() {
      return retryExchange;
    }

    public void setRetryExchange(String retryExchange) {
      this.retryExchange = retryExchange;
    }

    public String getDeadLetterExchange() {
      return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
      this.deadLetterExchange = deadLetterExchange;
    }

    public String getQueue() {
      return queue;
    }

    public void setQueue(String queue) {
      this.queue = queue;
    }

    public String getRetryQueue() {
      return retryQueue;
    }

    public void setRetryQueue(String retryQueue) {
      this.retryQueue = retryQueue;
    }

    public String getDeadLetterQueue() {
      return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
      this.deadLetterQueue = deadLetterQueue;
    }

    public String getRoutingKey() {
      return routingKey;
    }

    public void setRoutingKey(String routingKey) {
      this.routingKey = routingKey;
    }

    public String getRetryRoutingKey() {
      return retryRoutingKey;
    }

    public void setRetryRoutingKey(String retryRoutingKey) {
      this.retryRoutingKey = retryRoutingKey;
    }

    public String getDeadLetterRoutingKey() {
      return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
      this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public long getRetryDelayMs() {
      return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
      this.retryDelayMs = retryDelayMs;
    }

    public int getMaxRetries() {
      return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
    }
  }
}
