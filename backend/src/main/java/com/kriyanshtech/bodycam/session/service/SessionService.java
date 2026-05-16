package com.kriyanshtech.bodycam.session.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.kriyanshtech.bodycam.common.CreatedAtUuidCursor;
import com.kriyanshtech.bodycam.common.CreatedAtUuidCursorCodec;
import com.kriyanshtech.bodycam.common.CursorPageResponse;
import com.kriyanshtech.bodycam.common.CursorPaginationSupport;
import com.kriyanshtech.bodycam.common.NotFoundException;
import com.kriyanshtech.bodycam.common.PageResponse;
import com.kriyanshtech.bodycam.config.AppProperties;
import com.kriyanshtech.bodycam.session.dto.CreateSessionRequest;
import com.kriyanshtech.bodycam.session.dto.JoinSessionInviteRequest;
import com.kriyanshtech.bodycam.session.dto.JoinSessionTokenRequest;
import com.kriyanshtech.bodycam.session.dto.LiveKitTokenResponse;
import com.kriyanshtech.bodycam.session.dto.PublicSessionInviteResponse;
import com.kriyanshtech.bodycam.session.dto.SessionResponse;
import com.kriyanshtech.bodycam.session.dto.SessionInviteResponse;
import com.kriyanshtech.bodycam.session.entity.LiveSession;
import com.kriyanshtech.bodycam.session.entity.SessionInvite;
import com.kriyanshtech.bodycam.session.entity.SessionStatus;
import com.kriyanshtech.bodycam.session.repository.LiveSessionRepository;
import com.kriyanshtech.bodycam.session.repository.SessionInviteRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final long SESSION_INVITE_TTL_SECONDS = 10 * 60;

    private final LiveSessionRepository liveSessionRepository;
    private final SessionInviteRepository sessionInviteRepository;
    private final LiveKitTokenService liveKitTokenService;
    private final AppProperties appProperties;
    private final CreatedAtUuidCursorCodec cursorCodec;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(
            LiveSessionRepository liveSessionRepository,
            SessionInviteRepository sessionInviteRepository,
            LiveKitTokenService liveKitTokenService,
            AppProperties appProperties,
            CreatedAtUuidCursorCodec cursorCodec
    ) {
        this.liveSessionRepository = liveSessionRepository;
        this.sessionInviteRepository = sessionInviteRepository;
        this.liveKitTokenService = liveKitTokenService;
        this.appProperties = appProperties;
        this.cursorCodec = cursorCodec;
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
    public CursorPageResponse<SessionResponse> listActiveSessionsCursor(String cursor, int size) {
        List<LiveSession> sessions;
        if (cursor == null || cursor.trim().isEmpty()) {
            sessions = liveSessionRepository.findFirstActiveCursorPage(SessionStatus.ACTIVE, PageRequest.of(0, size + 1));
        } else {
            CreatedAtUuidCursor decodedCursor = cursorCodec.decode(cursor);
            sessions = liveSessionRepository.findNextActiveCursorPage(
                    SessionStatus.ACTIVE,
                    decodedCursor.createdAt(),
                    decodedCursor.id(),
                    PageRequest.of(0, size + 1));
        }

        return CursorPaginationSupport.buildPage(
                sessions,
                size,
                this::map,
                session -> cursorCodec.encode(new CreatedAtUuidCursor(session.getCreatedAt(), session.getId())));
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
        session.setWorkerId(request.workerId() != null ? request.workerId() : UUID.randomUUID());
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

    @Transactional
    public SessionInviteResponse createInvite(UUID sessionId, String requestedParticipantRole) {
        LiveSession session = findSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Only active sessions can be shared.");
        }

        SessionInvite invite = new SessionInvite();
        Instant now = Instant.now();
        invite.setId(UUID.randomUUID());
        invite.setSession(session);
        invite.setParticipantRole(resolveInviteParticipantRole(requestedParticipantRole));
        invite.setInviteToken(generateInviteToken());
        invite.setCreatedAt(now);
        invite.setExpiresAt(now.plusSeconds(SESSION_INVITE_TTL_SECONDS));

        SessionInvite savedInvite = sessionInviteRepository.save(invite);
        log.info(
                "Created session invite sessionId={} inviteId={} participantRole={} expiresAt={}",
                session.getId(),
                savedInvite.getId(),
                savedInvite.getParticipantRole(),
                savedInvite.getExpiresAt()
        );
        return mapInvite(savedInvite);
    }

    @Transactional
    public void revokeInvite(UUID sessionId, UUID inviteId) {
        LiveSession session = findSession(sessionId);
        SessionInvite invite = sessionInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Session invite not found: " + inviteId));
        if (!invite.getSession().getId().equals(session.getId())) {
            throw new IllegalArgumentException("Invite does not belong to the requested session.");
        }
        if (invite.getRevokedAt() != null) {
            return;
        }

        invite.setRevokedAt(Instant.now());
        log.info(
                "Revoked session invite sessionId={} inviteId={} participantRole={} revokedAt={}",
                session.getId(),
                invite.getId(),
                invite.getParticipantRole(),
                invite.getRevokedAt()
        );
    }

    @Transactional(readOnly = true)
    public PublicSessionInviteResponse getInvite(String inviteToken) {
        SessionInvite invite = requireReadableInvite(inviteToken);
        LiveSession session = invite.getSession();
        return new PublicSessionInviteResponse(
                session.getId(),
                session.getWorkerName(),
                session.getReferenceNumber(),
                session.getRoomName(),
                session.getStatus(),
                invite.getParticipantRole(),
                invite.getExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public LiveKitTokenResponse joinInvite(String inviteToken, JoinSessionInviteRequest request) {
        SessionInvite invite = requireActiveInvite(inviteToken);
        LiveSession session = invite.getSession();
        String token = liveKitTokenService.createJoinToken(
                request.participantName().trim(),
                session.getRoomName(),
                invite.getParticipantRole()
        );
        String publicUrl = appProperties.livekit().publicUrl();
        String liveKitUrl = publicUrl != null && !publicUrl.isBlank() ? publicUrl : appProperties.livekit().url();
        log.info(
                "Created public invite join token sessionId={} inviteId={} participantName={} participantRole={} liveKitUrl={}",
                session.getId(),
                invite.getId(),
                request.participantName(),
                invite.getParticipantRole(),
                liveKitUrl
        );
        return new LiveKitTokenResponse(token, session.getRoomName(), liveKitUrl);
    }

    private LiveSession findSession(UUID sessionId) {
        return liveSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    private SessionInvite requireReadableInvite(String inviteToken) {
        SessionInvite invite = sessionInviteRepository.findByInviteToken(inviteToken)
                .orElseThrow(() -> new NotFoundException("Session invite not found."));
        if (invite.getRevokedAt() != null) {
            throw new IllegalArgumentException("Session invite has been revoked.");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Session invite has expired.");
        }
        return invite;
    }

    private SessionInvite requireActiveInvite(String inviteToken) {
        SessionInvite invite = requireReadableInvite(inviteToken);
        if (invite.getSession().getStatus() != SessionStatus.ACTIVE) {
            throw new IllegalArgumentException("Session is no longer active.");
        }
        return invite;
    }

    private String resolveInviteParticipantRole(String requestedParticipantRole) {
        if (requestedParticipantRole == null || requestedParticipantRole.isBlank()) {
            return "BROWSER";
        }
        if (!List.of("WORKER", "OPERATOR", "BROWSER", "VIEWER").contains(requestedParticipantRole)) {
            throw new IllegalArgumentException("Unsupported session invite participant role.");
        }
        return requestedParticipantRole;
    }

    private String generateInviteToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private SessionInviteResponse mapInvite(SessionInvite invite) {
        LiveSession session = invite.getSession();
        return new SessionInviteResponse(
                invite.getId(),
                session.getId(),
                session.getWorkerName(),
                session.getReferenceNumber(),
                session.getRoomName(),
                session.getStatus(),
                invite.getParticipantRole(),
                invite.getInviteToken(),
                "/join/" + invite.getInviteToken(),
                invite.getExpiresAt(),
                invite.getCreatedAt()
        );
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
