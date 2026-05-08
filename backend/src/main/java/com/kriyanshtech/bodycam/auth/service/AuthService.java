package com.kriyanshtech.bodycam.auth.service;

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

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return new LoginResponse(
                jwtService.generateAccessToken(user),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole()
        );
    }

    public CurrentUserResponse currentUser(JwtService.AuthenticatedUser user) {
        return new CurrentUserResponse(user.userId(), user.username(), user.displayName(), user.role());
    }
}
