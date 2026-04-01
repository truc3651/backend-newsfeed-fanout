package com.backend.fanout.handlers;

import org.springframework.stereotype.Component;

import com.backend.core.security.UserClient;
import com.backend.fanout.dtos.PostCreatedEventDto;
import com.backend.fanout.repositories.NewsfeedRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedHandler implements DefaultHandler {
  private final EventParser eventParser;
  private final NewsfeedRepository newsfeedRepository;
  private final UserClient userClient;

  @Override
  public boolean support(Object o) {
    return o instanceof PostCreatedEventDto;
  }

  @Override
  public Mono<Void> handle(String json) {
    PostCreatedEventDto event = eventParser.parse(json, PostCreatedEventDto.class);
    String authorId = event.getAuthorId();
    String postId = event.getPostId();

    return userClient
        .getFriendIds(authorId)
        .flatMap(
            friendIds -> {
              if (friendIds.isEmpty()) {
                return Mono.empty();
              }

              log.info(
                  "Fanning out post {} to {} friends of author {}",
                  postId,
                  friendIds.size(),
                  authorId);

              return newsfeedRepository
                  .addToFeedsBatch(friendIds, postId, authorId)
                  .doOnSuccess(
                      count ->
                          log.info(
                              "Fanout complete for post {}: {} feeds updated out of {} friends",
                              postId,
                              count,
                              friendIds.size()));
            })
        .onErrorResume(
            e -> {
              log.error("Error during fanout for post {}: {}", postId, e.getMessage());
              return Mono.empty();
            })
        .then();
  }
}
