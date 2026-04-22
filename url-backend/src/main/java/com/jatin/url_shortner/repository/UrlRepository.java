package com.jatin.url_shortner.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jatin.url_shortner.entity.Url;
import com.jatin.url_shortner.entity.User;

@Repository
public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByLongUrlAndUser(String longUrl, User user);

    Optional<Url> findByShortCode(String shortCode);

    List<Url> findAllByUser(User user);

    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.urlId = :id")
    void incrementClickCount(@Param("id") Long id);
}
