package com.kriyanshtech.bodycam.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.entity.SessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {

    List<LiveSession> findAllByOrderByCreatedAtDesc();

    Page<LiveSession> findAllByStatusOrderByCreatedAtDesc(SessionStatus status, Pageable pageable);

    @Query("SELECT s FROM LiveSession s WHERE s.status = :status ORDER BY s.createdAt DESC, s.id DESC")
    List<LiveSession> findFirstActiveCursorPage(@Param("status") SessionStatus status, Pageable pageable);

    @Query("SELECT s FROM LiveSession s WHERE s.status = :status AND (s.createdAt < :cursorCreatedAt OR (s.createdAt = :cursorCreatedAt AND s.id < :cursorId)) ORDER BY s.createdAt DESC, s.id DESC")
    List<LiveSession> findNextActiveCursorPage(
            @Param("status") SessionStatus status,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
