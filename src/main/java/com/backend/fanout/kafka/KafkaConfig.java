package com.backend.fanout.kafka;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;

import com.backend.fanout.kafka.settings.KafkaBootstrapServersProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {
  private final KafkaProperties kafkaProperties;
  private final KafkaBootstrapServersProvider bootstrapServersProvider;
  private final KafkaEventProperties kafkaEventProperties;

  @Bean
  public ReceiverOptions<String, String> receiverOptions() {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersProvider.provide());

    KafkaEventProperties.Topics topics = kafkaEventProperties.getTopics();

    return ReceiverOptions.<String, String>create(props)
        .subscription(
            List.of(topics.getPostCreated(), topics.getPostDeleted(), topics.getFriendBlocked()));
  }

  @Bean
  public ReactiveKafkaConsumerTemplate<String, String> reactiveKafkaConsumerTemplate(
      ReceiverOptions<String, String> receiverOptions) {
    return new ReactiveKafkaConsumerTemplate<>(receiverOptions);
  }

  @Bean
  public ReactiveKafkaProducerTemplate<String, String> reactiveKafkaProducerTemplate() {
    Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServersProvider.provide());
    return new ReactiveKafkaProducerTemplate<>(SenderOptions.create(props));
  }
}
