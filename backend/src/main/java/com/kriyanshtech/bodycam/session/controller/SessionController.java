package com.kriyanshtech.bodycam.session.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kriyanshtech.bodycam.session.dto.CreateSessionRequest;
import com.kriyanshtech.bodycam.session.dto.JoinSessionTokenRequest;
import com.kriyanshtech.bodycam.session.dto.LiveKitTokenResponse;
import com.kriyanshtech.bodycam.session.dto.SessionResponse;
import com.kriyanshtech.bodycam.session.service.SessionService;
import com.kriyanshtech.bodycam.common.PageResponse;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions() {
        log.info("Received request to list all sessions");
        return ResponseEntity.ok(sessionService.listSessions());
    }

    @GetMapping("/active")
    public ResponseEntity<PageResponse<SessionResponse>> listActiveSessions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        log.info("Received request to list active sessions page={} size={}", page, size);
        return ResponseEntity.ok(sessionService.listActiveSessions(page, size));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received request to fetch session sessionId={}", sessionId);
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        log.info(
                "Received create session request workerId={} workerName={} referenceNumber={}",
                request.workerId(),
                request.workerName(),
                request.referenceNumber()
        );
        return ResponseEntity.ok(sessionService.createSession(request));
    }

    @PostMapping("/{sessionId}/join-token")
    public ResponseEntity<LiveKitTokenResponse> joinToken(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody JoinSessionTokenRequest request
    ) {
        log.info(
                "Received join token request sessionId={} participantName={} participantRole={}",
                sessionId,
                request.participantName(),
                request.participantRole()
        );
        return ResponseEntity.ok(sessionService.joinToken(sessionId, request));
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<SessionResponse> endSession(@PathVariable("sessionId") UUID sessionId) {
        log.info("Received end session request sessionId={}", sessionId);
        return ResponseEntity.ok(sessionService.endSession(sessionId));
    }
}
