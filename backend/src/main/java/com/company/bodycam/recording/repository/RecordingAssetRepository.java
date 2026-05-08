package com.company.bodycam.recording.repository;

import com.company.bodycam.recording.entity.RecordingAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecordingAssetRepository extends JpaRepository<RecordingAsset, UUID> {

    List<RecordingAsset> findAllByOrderByCreatedAtDesc();
}
