package com.company.bodycam.auth.service;

import com.company.bodycam.auth.dto.CurrentUserResponse;
import com.company.bodycam.auth.dto.LoginRequest;
import com.company.bodycam.auth.dto.LoginResponse;
import com.company.bodycam.auth.entity.AppUser;
import com.company.bodycam.auth.repository.AppUserRepository;
import com.company.bodycam.common.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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
