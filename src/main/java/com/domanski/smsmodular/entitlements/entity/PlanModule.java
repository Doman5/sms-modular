package com.domanski.smsmodular.entitlements.entity;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "plan_modules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanModule {
	@Id
	private UUID id;
	@Column(name = "plan_version_id", nullable = false)
	private UUID planVersionId;
	@Column(name = "module_key", nullable = false, length = 64)
	private String moduleKey;
}
