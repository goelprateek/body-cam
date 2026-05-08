package com.kriyanshtech.bodycam.session.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.session.dto.CreateSessionRequest;
import com.kriyanshtech.bodycam.session.dto.JoinSessionTokenRequest;
import com.kriyanshtech.bodycam.session.dto.LiveKitTokenResponse;
import com.kriyanshtech.bodycam.session.dto.SessionResponse;
import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.entity.SessionStatus;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {

    private final LiveSessionRepository liveSessionRepository;
    private final LiveKitTokenService liveKitTokenService;
    private final AppProperties appProperties;

    public SessionService(
            LiveSessionRepository liveSessionRepository,
            LiveKitTokenService liveKitTokenService,
            AppProperties appProperties
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.liveKitTokenService = liveKitTokenService;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions() {
        return liveSessionRepository.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID sessionId) {
        return map(findSession(sessionId));
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        LiveSession session = new LiveSession();
        Instant now = Instant.now();

        session.setId(UUID.randomUUID());
        session.setWorkerId(request.workerId());
        session.setWorkerName(request.workerName());
        session.setRoomName("session-" + session.getId());
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(now);
        session.setCreatedAt(now);

        return map(liveSessionRepository.save(session));
    }

    @Transactional
    public SessionResponse endSession(UUID sessionId) {
        LiveSession session = findSession(sessionId);
        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        return map(session);
    }

    @Transactional(readOnly = true)
    public LiveKitTokenResponse joinToken(UUID sessionId, JoinSessionTokenRequest request) {
        LiveSession session = findSession(sessionId);
        String token = liveKitTokenService.createJoinToken(request.participantName(), session.getRoomName(), request.participantRole());
        String publicUrl = appProperties.livekit().publicUrl();
        String liveKitUrl = publicUrl != null && !publicUrl.isBlank() ? publicUrl : appProperties.livekit().url();
        return new LiveKitTokenResponse(token, session.getRoomName(), liveKitUrl);
    }

    private LiveSession findSession(UUID sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    private SessionResponse map(LiveSession session) {
        return new SessionResponse(
                session.getId(),
                session.getWorkerId(),
                session.getWorkerName(),
                session.getRoomName(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt()
        );
    }
}
