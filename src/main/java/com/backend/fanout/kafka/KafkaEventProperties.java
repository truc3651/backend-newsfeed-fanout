package com.backend.fanout.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-properties")
public class KafkaEventProperties {
  private Topics topics = new Topics();

  @Data
  public static class Topics {
    private String postCreated;
    private String postDeleted;
    private String friendBlocked;
    private String deadLetterQueue;
  }
}
