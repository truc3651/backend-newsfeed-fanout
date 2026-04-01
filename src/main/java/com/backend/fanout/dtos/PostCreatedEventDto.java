package com.backend.fanout.dtos;

import lombok.Data;

@Data
public class PostCreatedEventDto {
  private String postId;
  private String authorId;
}
