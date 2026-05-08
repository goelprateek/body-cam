package com.kriyanshtech.bodycam.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.session.entity.LiveSession;

import java.util.List;
import java.util.UUID;

public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {

    List<LiveSession> findAllByOrderByCreatedAtDesc();
}
