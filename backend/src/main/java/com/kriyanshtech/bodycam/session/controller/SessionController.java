package com.kriyanshtech.bodycam.session.controller;

import jakarta.validation.Valid;
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

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> listSessions() {
        return ResponseEntity.ok(sessionService.listSessions());
    }

    @GetMapping("/active")
    public ResponseEntity<PageResponse<SessionResponse>> listActiveSessions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(sessionService.listActiveSessions(page, size));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable("sessionId") UUID sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.ok(sessionService.createSession(request));
    }

    @PostMapping("/{sessionId}/join-token")
    public ResponseEntity<LiveKitTokenResponse> joinToken(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody JoinSessionTokenRequest request
    ) {
        return ResponseEntity.ok(sessionService.joinToken(sessionId, request));
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<SessionResponse> endSession(@PathVariable("sessionId") UUID sessionId) {
        return ResponseEntity.ok(sessionService.endSession(sessionId));
    }
}
