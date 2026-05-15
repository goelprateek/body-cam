package com.kriyanshtech.bodycam.session.repository;

import com.kriyanshtech.bodycam.session.entity.SessionInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SessionInviteRepository extends JpaRepository<SessionInvite, UUID> {

    @Query("SELECT invite FROM SessionInvite invite JOIN FETCH invite.session WHERE invite.inviteToken = :inviteToken")
    Optional<SessionInvite> findByInviteToken(@Param("inviteToken") String inviteToken);
}
