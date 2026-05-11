package com.kriyanshtech.bodycam.recording.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingAssetRepository extends JpaRepository<RecordingAsset, UUID> {

    @EntityGraph(attributePaths = {"session", "metadata", "transcript"})
    List<RecordingAsset> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"session", "metadata", "transcript"})
    Optional<RecordingAsset> findById(UUID id);

    @EntityGraph(attributePaths = {"session", "metadata", "transcript"})
    List<RecordingAsset> findBySession_IdOrderByCreatedAtAsc(UUID sessionId);

    @EntityGraph(attributePaths = {"session", "metadata", "transcript"})
    Optional<RecordingAsset> findByIdempotencyKey(String idempotencyKey);
}
