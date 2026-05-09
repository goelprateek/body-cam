package com.kriyanshtech.bodycam.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.entity.SessionStatus;

import java.util.List;
import java.util.UUID;

public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {

    List<LiveSession> findAllByOrderByCreatedAtDesc();

    Page<LiveSession> findAllByStatusOrderByCreatedAtDesc(SessionStatus status, Pageable pageable);
}
