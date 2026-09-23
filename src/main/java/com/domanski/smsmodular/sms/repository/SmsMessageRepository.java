package com.domanski.smsmodular.sms.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import com.domanski.smsmodular.sms.entity.SmsMessage;

public interface SmsMessageRepository extends JpaRepository<SmsMessage, UUID> {
	Optional<SmsMessage> findByTenantIdAndId(UUID tenantId, UUID id);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from SmsMessage s where s.tenantId = :tenantId and s.id = :id")
	Optional<SmsMessage> lock(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
	Optional<SmsMessage> findByProviderAndDeviceIdAndEventId(String provider, String deviceId, String eventId);
	@Query("select s from SmsMessage s where s.tenantId = :tenantId "
			+ "and s.receivedAt >= :from and s.receivedAt < :to "
			+ "and (:status is null or s.status = :status) "
			+ "and (:employeeId is null or s.employeeId = :employeeId) "
			+ "and (:reviewOnly = false or s.status = 'REVIEW_REQUIRED')")
	Page<SmsMessage> search(@Param("tenantId") UUID tenantId, @Param("from") Instant from,
			@Param("to") Instant to, @Param("status") String status,
			@Param("employeeId") UUID employeeId, @Param("reviewOnly") boolean reviewOnly, Pageable pageable);
	List<SmsMessage> findTop100ByReceivedAtBeforeAndSenderCipherIsNotNullOrderByReceivedAtAsc(Instant before);
}
