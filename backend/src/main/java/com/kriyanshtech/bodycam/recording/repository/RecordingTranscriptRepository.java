package com.kriyanshtech.bodycam.recording.repository;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;
import com.kriyanshtech.bodycam.recording.entity.RecordingTranscriptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RecordingTranscriptRepository extends JpaRepository<RecordingTranscript, UUID> {

    @EntityGraph(attributePaths = {"recording", "recording.metadata", "recording.session", "segments"})
    Optional<RecordingTranscript> findByRecording_Id(UUID recordingId);

    @EntityGraph(attributePaths = {"recording", "recording.metadata", "recording.session", "segments"})
    Optional<RecordingTranscript> findById(UUID transcriptId);

    @EntityGraph(attributePaths = {"recording", "recording.metadata", "recording.session"})
    Optional<RecordingTranscript> findFirstByStatusOrderByCreatedAtAsc(RecordingTranscriptStatus status);
}
