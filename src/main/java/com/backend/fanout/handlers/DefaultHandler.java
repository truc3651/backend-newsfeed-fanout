package com.backend.fanout.handlers;

import reactor.core.publisher.Mono;

public interface DefaultHandler {
  boolean support(Object o);

  Mono<Void> handle(String json);
}
