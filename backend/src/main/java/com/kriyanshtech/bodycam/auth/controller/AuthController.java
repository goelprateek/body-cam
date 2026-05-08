package com.kriyanshtech.bodycam.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kriyanshtech.bodycam.auth.dto.CurrentUserResponse;
import com.kriyanshtech.bodycam.auth.dto.LoginRequest;
import com.kriyanshtech.bodycam.auth.dto.LoginResponse;
import com.kriyanshtech.bodycam.auth.service.AuthService;
import com.kriyanshtech.bodycam.auth.service.JwtService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal JwtService.AuthenticatedUser user) {
        return ResponseEntity.ok(authService.currentUser(user));
    }
}
