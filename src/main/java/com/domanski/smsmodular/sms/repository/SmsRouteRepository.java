package com.domanski.smsmodular.sms.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.domanski.smsmodular.sms.entity.SmsRoute;

public interface SmsRouteRepository extends JpaRepository<SmsRoute, UUID> {
	List<SmsRoute> findByActiveTrueOrderByDeviceIdAscIdAsc();
	Optional<SmsRoute> findByIdAndActiveTrue(UUID id);
	Optional<SmsRoute> findByDeviceIdAndRecipientAndActiveTrue(String deviceId, String recipient);
	Optional<SmsRoute> findByDeviceIdAndSimNumberAndActiveTrue(String deviceId, Integer simNumber);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from SmsRoute r where r.id = :id and r.active = true")
	Optional<SmsRoute> lockActive(@Param("id") UUID id);
}
