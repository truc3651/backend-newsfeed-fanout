# backend-newsfeed-fanout

A reactive fanout service that maintains personalized newsfeed caches for users by listening to domain events from other backend services.

## Overview

`backend-fanout` sits between the post/user domains and the newsfeed read layer. It consumes Kafka events and writes pre-computed feed entries into per-user Redis sorted sets, so the newsfeed service can serve feeds in O(1).

```
backend-users ──┐
                ├──► Kafka ──► backend-fanout ──► Redis ZSet (per user)
backend-posts ──┘                                        │
                                                         ▼
                                               backend-newsfeed
                                           (hydrates from cache)
```

## How It Works

### Data Model

Each user has a Redis sorted set keyed by their user ID. Each member is stored as `postId:authorId`, scored by creation timestamp so the feed is always time-ordered.

```
Key:    feed:{userId}
Member: {postId}:{authorId}
Score:  epoch timestamp (ms)
```

The newsfeed service reads this sorted set and hydrates full content by fetching post details from `backend-posts` cache and author info from `backend-users` cache — neither of which requires a database hit on the hot path.

### Feed Cap

Each user's sorted set is capped at **800 records**. When a new post is fanned out and the set would exceed 800 members, the oldest entry (lowest score) is trimmed automatically. This keeps memory bounded while still covering a meaningful feed depth.

## Event Handlers

### `post.created`

1. Reads the author's friend IDs from the `backend-users` Redis cache
2. For each friend, writes `postId:authorId` into `feed:{friendId}` with the current timestamp as the score
3. If the set exceeds 800 members, trims the oldest entry

### `post.deleted`

1. Resolves the author's friend IDs from cache
2. Removes the matching `postId:authorId` member from every friend's sorted set

### `friend.blocked`

1. When user A blocks user B, scans user A's sorted set for all members ending in `:userId-B`
2. Removes all matched members — effectively purging the blocked user's posts from the feed

## Architecture

```
Kafka (reactive consumer)
        │
        ▼
   KafkaConsumer
        │
        ├── post.created  ──► PostCreatedHandler
        ├── post.deleted  ──► PostDeletedHandler
        └── friend.blocked ─► BlockFriendHandler
                                    │
                                    ▼
                           NewsfeedRepository
                         (Redis ZSet operations)
                                    │
                                    ▼
                        UserClient (WebClient)
                     (friend IDs from users cache)
```