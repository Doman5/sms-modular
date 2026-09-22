package com.domanski.smsmodular.identity.security;

import java.util.UUID;

public record CurrentPrincipal(UUID id, UUID tenantId, boolean platform, boolean mustChangePassword) {
}
