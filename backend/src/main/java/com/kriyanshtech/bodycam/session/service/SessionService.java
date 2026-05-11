package com.kriyanshtech.bodycam.session.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.common.PageResponse;
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
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

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
        List<SessionResponse> sessions = liveSessionRepository.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
        log.info("Listed {} sessions", sessions.size());
        return sessions;
    }

    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> listActiveSessions(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);

        var sessionsPage = liveSessionRepository.findAllByStatusOrderByCreatedAtDesc(
                SessionStatus.ACTIVE,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        log.info(
                "Loaded active sessions page request page={} size={} resolvedPage={} resolvedSize={} resultCount={} totalItems={} hasNext={}",
                page,
                size,
                safePage,
                safeSize,
                sessionsPage.getNumberOfElements(),
                sessionsPage.getTotalElements(),
                sessionsPage.hasNext()
        );

        return new PageResponse<>(
                sessionsPage.getContent().stream().map(this::map).toList(),
                sessionsPage.getNumber(),
                sessionsPage.getSize(),
                sessionsPage.getTotalElements(),
                sessionsPage.getTotalPages(),
                sessionsPage.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(UUID sessionId) {
        SessionResponse response = map(findSession(sessionId));
        log.info(
                "Fetched session sessionId={} status={} workerId={} referenceNumber={}",
                response.id(),
                response.status(),
                response.workerId(),
                response.referenceNumber()
        );
        return response;
    }

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        LiveSession session = new LiveSession();
        Instant now = Instant.now();

        session.setId(UUID.randomUUID());
        session.setWorkerId(request.workerId());
        session.setWorkerName(request.workerName());
        session.setReferenceNumber(request.referenceNumber().trim());
        session.setRoomName("session-" + session.getId());
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(now);
        session.setCreatedAt(now);

        LiveSession savedSession = liveSessionRepository.save(session);
        log.info(
                "Created session sessionId={} workerId={} workerName={} referenceNumber={} roomName={} startedAt={}",
                savedSession.getId(),
                savedSession.getWorkerId(),
                savedSession.getWorkerName(),
                savedSession.getReferenceNumber(),
                savedSession.getRoomName(),
                savedSession.getStartedAt()
        );
        return map(savedSession);
    }

    @Transactional
    public SessionResponse endSession(UUID sessionId) {
        LiveSession session = findSession(sessionId);
        SessionStatus previousStatus = session.getStatus();
        session.setStatus(SessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        log.info(
                "Ended session sessionId={} previousStatus={} endedAt={} referenceNumber={}",
                session.getId(),
                previousStatus,
                session.getEndedAt(),
                session.getReferenceNumber()
        );
        return map(session);
    }

    @Transactional(readOnly = true)
    public LiveKitTokenResponse joinToken(UUID sessionId, JoinSessionTokenRequest request) {
        LiveSession session = findSession(sessionId);
        log.info(
                "Creating LiveKit join token sessionId={} roomName={} participantName={} participantRole={} referenceNumber={}",
                session.getId(),
                session.getRoomName(),
                request.participantName(),
                request.participantRole(),
                session.getReferenceNumber()
        );
        String token = liveKitTokenService.createJoinToken(request.participantName(), session.getRoomName(), request.participantRole());
        String publicUrl = appProperties.livekit().publicUrl();
        String liveKitUrl = publicUrl != null && !publicUrl.isBlank() ? publicUrl : appProperties.livekit().url();
        log.info(
                "Created LiveKit join token sessionId={} roomName={} participantRole={} liveKitUrl={}",
                session.getId(),
                session.getRoomName(),
                request.participantRole(),
                liveKitUrl
        );
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
                session.getReferenceNumber(),
                session.getRoomName(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCreatedAt()
        );
    }
}
