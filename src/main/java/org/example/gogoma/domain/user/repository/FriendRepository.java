package org.example.gogoma.domain.user.repository;

import org.example.gogoma.domain.user.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FriendRepository extends JpaRepository<Friend, Integer> {
    boolean existsByUserIdAndFriendId(int userId, int friendId);
}
