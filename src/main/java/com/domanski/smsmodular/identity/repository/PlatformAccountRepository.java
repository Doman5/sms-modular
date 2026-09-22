package com.domanski.smsmodular.identity.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.domanski.smsmodular.identity.entity.PlatformAccount;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccount, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from PlatformAccount account where account.normalizedEmail = :email")
	Optional<PlatformAccount> findLockedByEmail(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select account from PlatformAccount account where account.id = :id")
	Optional<PlatformAccount> findLockedById(@Param("id") UUID id);
}
