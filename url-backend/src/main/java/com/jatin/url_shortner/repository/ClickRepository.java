package com.jatin.url_shortner.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jatin.url_shortner.entity.Click;

@Repository
public interface ClickRepository extends JpaRepository<Click, Long> {
    
}
