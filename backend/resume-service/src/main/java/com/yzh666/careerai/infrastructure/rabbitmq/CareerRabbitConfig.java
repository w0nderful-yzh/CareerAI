package com.yzh666.careerai.infrastructure.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CareerRabbitProperties.class)
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CareerRabbitConfig {

  @Bean
  DirectExchange jobMatchExchange(CareerRabbitProperties properties) {
    return new DirectExchange(properties.getJobMatch().getExchange(), true, false);
  }

  @Bean
  DirectExchange jobMatchRetryExchange(CareerRabbitProperties properties) {
    return new DirectExchange(properties.getJobMatch().getRetryExchange(), true, false);
  }

  @Bean
  DirectExchange jobMatchDeadLetterExchange(CareerRabbitProperties properties) {
    return new DirectExchange(properties.getJobMatch().getDeadLetterExchange(), true, false);
  }

  @Bean
  Queue jobMatchQueue(CareerRabbitProperties properties) {
    CareerRabbitProperties.JobMatch jobMatch = properties.getJobMatch();
    return QueueBuilder.durable(jobMatch.getQueue())
        .deadLetterExchange(jobMatch.getDeadLetterExchange())
        .deadLetterRoutingKey(jobMatch.getDeadLetterRoutingKey())
        .build();
  }

  @Bean
  Queue jobMatchRetryQueue(CareerRabbitProperties properties) {
    CareerRabbitProperties.JobMatch jobMatch = properties.getJobMatch();
    return QueueBuilder.durable(jobMatch.getRetryQueue())
        .ttl((int) jobMatch.getRetryDelayMs())
        .deadLetterExchange(jobMatch.getExchange())
        .deadLetterRoutingKey(jobMatch.getRoutingKey())
        .build();
  }

  @Bean
  Queue jobMatchDeadLetterQueue(CareerRabbitProperties properties) {
    return QueueBuilder.durable(properties.getJobMatch().getDeadLetterQueue()).build();
  }

  @Bean
  Binding jobMatchBinding(
      Queue jobMatchQueue,
      DirectExchange jobMatchExchange,
      CareerRabbitProperties properties
  ) {
    return BindingBuilder.bind(jobMatchQueue)
        .to(jobMatchExchange)
        .with(properties.getJobMatch().getRoutingKey());
  }

  @Bean
  Binding jobMatchRetryBinding(
      Queue jobMatchRetryQueue,
      DirectExchange jobMatchRetryExchange,
      CareerRabbitProperties properties
  ) {
    return BindingBuilder.bind(jobMatchRetryQueue)
        .to(jobMatchRetryExchange)
        .with(properties.getJobMatch().getRetryRoutingKey());
  }

  @Bean
  Binding jobMatchDeadLetterBinding(
      Queue jobMatchDeadLetterQueue,
      DirectExchange jobMatchDeadLetterExchange,
      CareerRabbitProperties properties
  ) {
    return BindingBuilder.bind(jobMatchDeadLetterQueue)
        .to(jobMatchDeadLetterExchange)
        .with(properties.getJobMatch().getDeadLetterRoutingKey());
  }

  @Bean
  SimpleRabbitListenerContainerFactory manualAckRabbitListenerContainerFactory(
      ConnectionFactory connectionFactory
  ) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setDefaultRequeueRejected(false);
    return factory;
  }
}
