package com.kriyanshtech.bodycam.recording.repository;

import com.kriyanshtech.bodycam.recording.entity.SessionRecordingExport;
import com.kriyanshtech.bodycam.recording.entity.SessionRecordingExportStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRecordingExportRepository extends JpaRepository<SessionRecordingExport, UUID> {

    @EntityGraph(attributePaths = {"session"})
    Optional<SessionRecordingExport> findTopBySession_IdOrderByCreatedAtDesc(UUID sessionId);

    @EntityGraph(attributePaths = {"session"})
    Optional<SessionRecordingExport> findFirstByStatusOrderByCreatedAtAsc(SessionRecordingExportStatus status);

    @EntityGraph(attributePaths = {"session"})
    Optional<SessionRecordingExport> findById(UUID exportId);
}
