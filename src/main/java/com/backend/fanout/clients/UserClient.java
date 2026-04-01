package com.backend.fanout.clients;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import com.backend.fanout.exceptions.ServiceUnavailableException;

@Component
public class UserClient {
  private final WebClient webClient;

  public UserClient(
      WebClient.Builder webClientBuilder,
      @Value("${services.users-management.url:http://localhost:8090}") String usersManagementUrl) {
    this.webClient = webClientBuilder.baseUrl(usersManagementUrl).build();
  }

  public Mono<List<String>> getFriendIds(String userId) {
    return webClient
        .get()
        .uri("/v1/api/friendships/{userId}/friends", userId)
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
        .retryWhen(
            Retry.backoff(3, Duration.ofMillis(200))
                .filter(ex -> ex instanceof WebClientRequestException))
        .onErrorMap(ex -> new ServiceUnavailableException("User service is unavailable", ex));
  }
}
