package com.backend.fanout.kafka.settings;

public interface KafkaBootstrapServersProvider {
  String provide();
}
