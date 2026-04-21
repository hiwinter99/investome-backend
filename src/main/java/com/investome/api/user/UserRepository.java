package com.investome.api.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByNickname(String nickname);

    @Query("select u from User u where lower(trim(u.email)) = lower(trim(:email))")
    Optional<User> findByNormalizedEmail(@Param("email") String email);
}
