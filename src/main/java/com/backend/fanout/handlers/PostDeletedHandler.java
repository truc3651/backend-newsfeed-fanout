package com.backend.fanout.handlers;

import org.springframework.stereotype.Component;

import com.backend.core.security.UserClient;
import com.backend.fanout.dtos.PostCreatedEventDto;
import com.backend.fanout.dtos.PostDeletedEventDto;
import com.backend.fanout.repositories.NewsfeedRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostDeletedHandler implements DefaultHandler {
  private final NewsfeedRepository newsfeedRepository;
  private final EventParser eventParser;
  private final UserClient userClient;

  @Override
  public boolean support(Object o) {
    return o instanceof PostCreatedEventDto;
  }

  @Override
  public Mono<Void> handle(String json) {
    PostDeletedEventDto event = eventParser.parse(json, PostDeletedEventDto.class);
    String authorId = event.getAuthorId();
    String postId = event.getPostId();

    return userClient
        .getFriendIds(authorId)
        .flatMap(
            friendIds -> {
              if (friendIds.isEmpty()) {
                return Mono.empty();
              }

              log.info("Fanning out DELETE for post {} to {} friends", postId, friendIds.size());

              return newsfeedRepository
                  .removeFromFeedsBatch(friendIds, postId, authorId)
                  .doOnSuccess(
                      count ->
                          log.info(
                              "Delete fanout complete for post {}: {} feeds updated",
                              postId,
                              count));
            })
        .onErrorResume(
            e -> {
              log.error("Error during delete fanout for post {}: {}", postId, e.getMessage());
              return Mono.empty();
            })
        .then();
  }
}
