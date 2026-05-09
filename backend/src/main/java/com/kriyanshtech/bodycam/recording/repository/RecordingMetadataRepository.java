package com.kriyanshtech.bodycam.recording.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.recording.entity.RecordingMetadata;

import java.util.UUID;

public interface RecordingMetadataRepository extends JpaRepository<RecordingMetadata, UUID> {
}
