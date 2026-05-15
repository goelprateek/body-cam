package com.kriyanshtech.bodycam.session.controller;

import com.kriyanshtech.bodycam.session.dto.CreateSessionInviteRequest;
import com.kriyanshtech.bodycam.session.dto.JoinSessionInviteRequest;
import com.kriyanshtech.bodycam.session.dto.LiveKitTokenResponse;
import com.kriyanshtech.bodycam.session.dto.PublicSessionInviteResponse;
import com.kriyanshtech.bodycam.session.dto.SessionInviteResponse;
import com.kriyanshtech.bodycam.session.service.SessionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class SessionInviteController {
    private static final Logger log = LoggerFactory.getLogger(SessionInviteController.class);

    private final SessionService sessionService;

    public SessionInviteController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/api/sessions/{sessionId}/invites")
    public ResponseEntity<SessionInviteResponse> createInvite(
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody(required = false) CreateSessionInviteRequest request
    ) {
        String participantRole = request != null ? request.participantRole() : null;
        log.info("Received session invite create request sessionId={} participantRole={}", sessionId, participantRole);
        return ResponseEntity.ok(sessionService.createInvite(sessionId, participantRole));
    }

    @DeleteMapping("/api/sessions/{sessionId}/invites/{inviteId}")
    public ResponseEntity<Void> revokeInvite(
            @PathVariable("sessionId") UUID sessionId,
            @PathVariable("inviteId") UUID inviteId
    ) {
        log.info("Received session invite revoke request sessionId={} inviteId={}", sessionId, inviteId);
        sessionService.revokeInvite(sessionId, inviteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/session-invites/{inviteToken}")
    public ResponseEntity<PublicSessionInviteResponse> getInvite(@PathVariable("inviteToken") String inviteToken) {
        log.info("Received session invite lookup tokenPrefix={}", inviteToken.substring(0, Math.min(inviteToken.length(), 8)));
        return ResponseEntity.ok(sessionService.getInvite(inviteToken));
    }

    @PostMapping("/api/session-invites/{inviteToken}/join-token")
    public ResponseEntity<LiveKitTokenResponse> joinInvite(
            @PathVariable("inviteToken") String inviteToken,
            @Valid @RequestBody JoinSessionInviteRequest request
    ) {
        log.info(
                "Received public invite join token request tokenPrefix={} participantName={}",
                inviteToken.substring(0, Math.min(inviteToken.length(), 8)),
                request.participantName()
        );
        return ResponseEntity.ok(sessionService.joinInvite(inviteToken, request));
    }
}
