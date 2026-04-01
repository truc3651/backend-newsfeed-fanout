package com.backend.fanout.kafka;

import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;

import com.backend.fanout.handlers.DefaultHandler;
import com.backend.fanout.handlers.PostCreatedHandler;
import com.backend.fanout.handlers.PostDeletedHandler;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumer {
  private final ReactiveKafkaConsumerTemplate<String, String> kafkaTemplate;
  private final ReactiveKafkaProducerTemplate<String, String> producerTemplate;
  private final KafkaEventProperties kafkaEventProperties;
  private final PostCreatedHandler postCreatedHandler;
  private final PostDeletedHandler postDeletedHandler;

  @PostConstruct
  public void consume() {
    kafkaTemplate
        .receive()
        .flatMap(
            record ->
                routeToHandler(record.topic(), record.value())
                    .then(Mono.fromRunnable(() -> record.receiverOffset().acknowledge()))
                    .onErrorResume(
                        error ->
                            sendToDlq(record, error)
                                .then(
                                    Mono.fromRunnable(
                                        () -> record.receiverOffset().acknowledge()))))
        .subscribe();
  }

  private Mono<Void> routeToHandler(String topic, String value) {
    KafkaEventProperties.Topics topics = kafkaEventProperties.getTopics();

    if (topic.equals(topics.getPostCreated())) {
      return processEvent(value, postCreatedHandler);
    }
    if (topic.equals(topics.getPostDeleted())) {
      return processEvent(value, postDeletedHandler);
    }
    return Mono.empty();
  }

  private Mono<Void> processEvent(String json, DefaultHandler handler) {
    if (handler.support(json)) {
      return handler.handle(json);
    }
    return Mono.empty();
  }

  private Mono<Void> sendToDlq(ReceiverRecord<String, String> record, Throwable error) {
    log.error(
        "Sending to DLQ — topic: {}, partition: {}, offset: {}, key: {}, error: {}",
        record.topic(),
        record.partition(),
        record.offset(),
        record.key(),
        error.getMessage());
    String deadLetterTopic = kafkaEventProperties.getTopics().getDeadLetterQueue();

    return producerTemplate
        .send(deadLetterTopic, record.key(), record.value())
        .doOnSuccess(result -> log.info("Sent to DLQ topic: {}", deadLetterTopic))
        .onErrorResume(
            dlqError -> {
              log.error(
                  "Failed to send to DLQ topic: {}, error: {}",
                  deadLetterTopic,
                  dlqError.getMessage());
              return Mono.empty();
            })
        .then();
  }
}
