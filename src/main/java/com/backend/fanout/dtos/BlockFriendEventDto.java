package com.backend.fanout.dtos;

import lombok.Data;

@Data
public class BlockFriendEventDto {
  private String userId;
  private String formerFriendId;
}
