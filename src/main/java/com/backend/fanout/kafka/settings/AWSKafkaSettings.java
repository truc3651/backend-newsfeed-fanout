package com.backend.fanout.kafka.settings;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.jsonwebtoken.lang.Strings;

public class AWSKafkaSettings {
  @JsonProperty("bootstrap_brokers_tls")
  private String bootstrapBrokersTls;

  @JsonProperty("bootstrap_brokers")
  private String bootstrapBrokers;

  public String getFirstTlsBroker() {
    if (Strings.hasText(bootstrapBrokersTls)) {
      return bootstrapBrokersTls.split(",")[0];
    }
    return null;
  }
}
