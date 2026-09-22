package com.domanski.smsmodular.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.domanski.smsmodular.identity.entity.AccountStatus;
import com.domanski.smsmodular.identity.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where user.normalizedEmail = :email")
	Optional<UserAccount> findLockedByEmail(@Param("email") String email);

	Optional<UserAccount> findByTenantIdAndId(UUID tenantId, UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where user.tenantId = :tenantId and user.id = :id")
	Optional<UserAccount> findLockedByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

	Page<UserAccount> findByTenantId(UUID tenantId, Pageable pageable);

	List<UserAccount> findByTenantIdAndStatus(UUID tenantId, AccountStatus status);

	boolean existsByTenantIdAndRoleId(UUID tenantId, UUID roleId);
}
