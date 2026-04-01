package com.backend.fanout.dtos;

import lombok.Data;

@Data
public class PostDeletedEventDto {
  private String postId;
  private String authorId;
}
