package com.backend.fanout.handlers;

import org.springframework.stereotype.Component;

import com.backend.fanout.dtos.BlockFriendEventDto;
import com.backend.fanout.repositories.NewsfeedRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockFriendEventHandler implements DefaultHandler {
  private final NewsfeedRepository newsfeedRepository;
  private final EventParser eventParser;

  @Override
  public boolean support(Object o) {
    return o instanceof BlockFriendEventDto;
  }

  @Override
  public Mono<Void> handle(String json) {
    BlockFriendEventDto event = eventParser.parse(json, BlockFriendEventDto.class);
    String userId = event.getUserId();
    String formerFriendId = event.getFormerFriendId();

    return newsfeedRepository
        .removeAllPostsByAuthor(userId, formerFriendId)
        .doOnSuccess(
            removed ->
                log.info("Removed {} posts by {} from {}'s feed", removed, formerFriendId, userId))
        .then();
  }
}
