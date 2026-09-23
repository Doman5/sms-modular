package com.domanski.smsmodular.integrationruntime.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.integrationruntime.entity.InboxReceipt;

public interface InboxReceiptRepository extends JpaRepository<InboxReceipt, UUID> {
	Optional<InboxReceipt> findByProviderAndDeviceIdAndEventId(String provider, String deviceId, String eventId);
}
