package com.kriyanshtech.bodycam.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.kriyanshtech.bodycam.auth.dto.CurrentUserResponse;
import com.kriyanshtech.bodycam.auth.dto.LoginRequest;
import com.kriyanshtech.bodycam.auth.dto.LoginResponse;
import com.kriyanshtech.bodycam.auth.entity.AppUser;
import com.kriyanshtech.bodycam.auth.repository.AppUserRepository;
import com.kriyanshtech.bodycam.common.UnauthorizedException;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        log.info("Authenticating login username={}", request.username());
        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> {
                    log.warn("Rejected login for username={} because the user was not found", request.username());
                    return new UnauthorizedException("Invalid username or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Rejected login for username={} due to password mismatch", request.username());
            throw new UnauthorizedException("Invalid username or password");
        }

        log.info("Authenticated login userId={} username={} role={}", user.getId(), user.getUsername(), user.getRole());

        return new LoginResponse(
                jwtService.generateAccessToken(user),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    public CurrentUserResponse currentUser(JwtService.AuthenticatedUser user) {
        log.info("Resolved current user userId={} username={} role={}", user.userId(), user.username(), user.role());
        return new CurrentUserResponse(user.userId(), user.username(), user.displayName(), user.role());
    }
}
