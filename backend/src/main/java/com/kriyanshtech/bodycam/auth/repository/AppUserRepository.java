package com.kriyanshtech.bodycam.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kriyanshtech.bodycam.auth.entity.AppUser;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
