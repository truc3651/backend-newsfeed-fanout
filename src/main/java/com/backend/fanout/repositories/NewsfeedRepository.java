package com.backend.fanout.repositories;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Repository
@RequiredArgsConstructor
public class NewsfeedRepository {
  private final ReactiveRedisTemplate<String, String> redisTemplate;
  private static final String NEWSFEED_KEY_PREFIX = "newsfeed:";

  @Value("${newsfeed.max-feed-size}")
  private int maxFeedSize;

  @Value("${newsfeed.feed-ttl-seconds}")
  private long feedTtlSeconds;

  public Mono<Boolean> addToFeed(String userId, String postId, String authorId) {
    String key = buildKey(userId);
    String member = buildMember(postId, authorId);

    return redisTemplate
        .opsForZSet()
        .add(key, member, System.currentTimeMillis())
        .flatMap(added -> trimFeed(key).thenReturn(added))
        .flatMap(added -> refreshTtl(key).thenReturn(added));
  }

  public Mono<Long> addToFeedsBatch(List<String> userIds, String postId, String authorId) {
    return Flux.fromIterable(userIds)
        .flatMap(
            userId ->
                addToFeed(userId, postId, authorId)
                    .map(added -> added ? 1L : 0L)
                    .onErrorResume(e -> logAndReturnZero("add", postId, userId, e)),
            16)
        .reduce(0L, Long::sum);
  }

  public Mono<Boolean> removeFromFeed(String userId, String postId, String authorId) {
    String key = buildKey(userId);
    String member = buildMember(postId, authorId);

    return redisTemplate
        .opsForZSet()
        .remove(key, member)
        .map(removed -> Objects.nonNull(removed) && removed > 0);
  }

  public Mono<Long> removeFromFeedsBatch(List<String> userIds, String postId, String authorId) {
    return Flux.fromIterable(userIds)
        .flatMap(
            userId ->
                removeFromFeed(userId, postId, authorId)
                    .map(removed -> removed ? 1L : 0L)
                    .onErrorResume(e -> logAndReturnZero("remove", postId, userId, e)),
            16)
        .reduce(0L, Long::sum);
  }

  public Mono<Long> removeAllPostsByAuthor(String userId, String authorId) {
    String key = buildKey(userId);
    String authorSuffix = ":" + authorId;

    return redisTemplate
        .opsForZSet()
        .range(key, Range.closed(0L, -1L))
        .filter(member -> member.endsWith(authorSuffix))
        .collectList()
        .flatMap(toRemove -> removeMembers(key, toRemove, authorId, userId));
  }

  private Mono<Void> trimFeed(String key) {
    return redisTemplate
        .opsForZSet()
        .size(key)
        .flatMap(
            feedSize -> {
              if (feedSize <= maxFeedSize) {
                return Mono.empty();
              }
              long removeCount = feedSize - maxFeedSize;
              return redisTemplate
                  .opsForZSet()
                  .removeRange(key, Range.closed(0L, removeCount - 1))
                  .then();
            });
  }

  private Mono<Boolean> refreshTtl(String key) {
    return redisTemplate.expire(key, Duration.ofSeconds(feedTtlSeconds));
  }

  private Mono<Long> removeMembers(
      String key, List<String> toRemove, String authorId, String userId) {
    if (toRemove.isEmpty()) {
      return Mono.just(0L);
    }
    return redisTemplate
        .opsForZSet()
        .remove(key, toRemove.toArray())
        .doOnNext(
            removed ->
                log.info(
                    "Removed {} posts by author {} from user {}'s feed",
                    removed,
                    authorId,
                    userId));
  }

  private Mono<Long> logAndReturnZero(String operation, String postId, String userId, Throwable e) {
    log.error(
        "Failed to {} post {} from feed of user {}: {}", operation, postId, userId, e.getMessage());
    return Mono.just(0L);
  }

  private String buildKey(String userId) {
    return NEWSFEED_KEY_PREFIX + userId;
  }

  private String buildMember(String postId, String authorId) {
    return postId + ":" + authorId;
  }
}
