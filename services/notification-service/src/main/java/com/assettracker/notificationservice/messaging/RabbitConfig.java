package com.assettracker.notificationservice.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the durable queue that this service reads and binds it to every {@code assignment.*}
 * routing key on the events exchange.
 */
@Configuration
public class RabbitConfig {

  @Bean
  public TopicExchange eventsExchange(@Value("${messaging.exchange}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  public Queue notificationQueue(@Value("${messaging.queue}") String name) {
    return QueueBuilder.durable(name).build();
  }

  @Bean
  public Binding notificationBinding(Queue notificationQueue, TopicExchange eventsExchange) {
    return BindingBuilder.bind(notificationQueue).to(eventsExchange).with("assignment.#");
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
