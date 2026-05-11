package com.kriyanshtech.bodycam.recording.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.recording.entity.RecordingTranscript;

import java.util.Optional;
import java.util.UUID;

public interface RecordingTranscriptRepository extends JpaRepository<RecordingTranscript, UUID> {

    Optional<RecordingTranscript> findByRecording_Id(UUID recordingId);
}
