package com.assettracker.assignmentservice.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the events exchange and JSON message conversion for the publisher. */
@Configuration
public class RabbitConfig {

  @Bean
  public TopicExchange eventsExchange(@Value("${messaging.exchange}") String name) {
    return new TopicExchange(name, true, false);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
