package com.company.bodycam.session.repository;

import com.company.bodycam.session.entity.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {

    List<LiveSession> findAllByOrderByCreatedAtDesc();
}
