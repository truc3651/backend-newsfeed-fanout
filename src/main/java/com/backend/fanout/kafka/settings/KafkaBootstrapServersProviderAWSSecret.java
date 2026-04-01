package com.backend.fanout.kafka.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.backend.core.exceptions.AWSException;
import com.backend.core.exceptions.ConfigurationException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DecryptionFailureException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.InternalServiceErrorException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidParameterException;
import software.amazon.awssdk.services.secretsmanager.model.InvalidRequestException;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

@Component
@Profile("!localdev & !test")
public class KafkaBootstrapServersProviderAWSSecret implements KafkaBootstrapServersProvider {
  private static final String AWS_KAFKA_SECRET_NAME_PARAM = "AWS_KAFKA_SECRET_NAME";
  private static final String AWS_REGION = "AWS_REGION";

  private final Environment environment;
  private final SecretsManagerClient secretsManagerClient;
  private final ObjectMapper objectMapper;

  public KafkaBootstrapServersProviderAWSSecret(
      Environment environment, ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.environment = environment;
    this.secretsManagerClient = initializeSecretsManagerClient();
  }

  @Override
  public String provide() {
    try {
      AWSKafkaSettings kafkaSecret =
          objectMapper.readValue(getSecretJson(), AWSKafkaSettings.class);
      return kafkaSecret.getFirstTlsBroker();
    } catch (IOException e) {
      throw new AWSException(e);
    }
  }

  private SecretsManagerClient initializeSecretsManagerClient() {
    String region = assertRequiredEnvironmentParam(AWS_REGION, "AWS Region is not defined");
    return SecretsManagerClient.builder().region(Region.of(region)).build();
  }

  private String assertRequiredEnvironmentParam(String paramName, String errorMsg) {
    return Optional.ofNullable(environment.getProperty(paramName))
        .orElseThrow(() -> new ConfigurationException(errorMsg));
  }

  private String getSecretJson() {
    String secretName =
        assertRequiredEnvironmentParam(
            AWS_KAFKA_SECRET_NAME_PARAM, "AWS Kafka Secret Name is not defined");

    try {
      GetSecretValueRequest getSecretValueRequest =
          GetSecretValueRequest.builder().secretId(secretName).build();
      GetSecretValueResponse secretValueResult =
          secretsManagerClient.getSecretValue(getSecretValueRequest);
      return new String(
          Base64.getDecoder().decode(secretValueResult.secretBinary().asByteBuffer()).array(),
          StandardCharsets.UTF_8);
    } catch (DecryptionFailureException
        | InternalServiceErrorException
        | InvalidParameterException
        | InvalidRequestException
        | ResourceNotFoundException e) {
      throw new AWSException(e);
    }
  }
}
