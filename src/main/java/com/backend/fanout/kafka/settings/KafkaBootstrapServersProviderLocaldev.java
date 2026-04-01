package com.backend.fanout.kafka.settings;

import java.util.List;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@Profile({"localdev", "test"})
@AllArgsConstructor
public class KafkaBootstrapServersProviderLocaldev implements KafkaBootstrapServersProvider {
  private final KafkaProperties kafkaProperties;

  @Override
  public String provide() {
    List<String> servers = kafkaProperties.getBootstrapServers();
    if (servers.size() == 1) {
      return servers.get(0);
    }
    return String.join(",", servers);
  }
}
