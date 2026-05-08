package com.kriyanshtech.bodycam.recording.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.recording.entity.RecordingAsset;

import java.util.List;
import java.util.UUID;

public interface RecordingAssetRepository extends JpaRepository<RecordingAsset, UUID> {

    List<RecordingAsset> findAllByOrderByCreatedAtDesc();
}
