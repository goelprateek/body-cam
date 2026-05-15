package com.kriyanshtech.bodycam.recording.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface RecordingAssetRepository extends JpaRepository<RecordingAsset, UUID> {

    @EntityGraph(attributePaths = { "session", "metadata", "transcript" })
    @Query("SELECT r FROM RecordingAsset r ORDER BY r.createdAt DESC, r.id DESC")
    List<RecordingAsset> findFirstPage(Pageable pageable);

    @EntityGraph(attributePaths = { "session", "metadata", "transcript" })
    @Query("SELECT r FROM RecordingAsset r WHERE r.createdAt < :cursorCreatedAt OR (r.createdAt = :cursorCreatedAt AND r.id < :cursorId) ORDER BY r.createdAt DESC, r.id DESC")
    List<RecordingAsset> findNextPage(@Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId, Pageable pageable);

    @EntityGraph(attributePaths = { "session", "metadata", "transcript", "transcript.segments" })
    List<RecordingAsset> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "session", "metadata", "transcript", "transcript.segments" })
    Optional<RecordingAsset> findById(UUID id);

    @EntityGraph(attributePaths = { "session", "metadata", "transcript", "transcript.segments" })
    List<RecordingAsset> findBySession_IdOrderByCreatedAtAsc(UUID sessionId);

    @EntityGraph(attributePaths = { "session", "metadata", "transcript", "transcript.segments" })
    Optional<RecordingAsset> findByIdempotencyKey(String idempotencyKey);
}
