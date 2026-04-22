package com.jatin.url_shortner.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jatin.url_shortner.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>{
    
    Optional<User> findByUsername(String username);
}
