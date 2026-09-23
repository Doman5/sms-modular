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
@Table(name = "plan_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanVersion {
	@Id
	private UUID id;
	@Column(name = "plan_id", nullable = false)
	private UUID planId;
	@Column(nullable = false)
	private int version;
	@Column(name = "active_user_limit_mode", nullable = false, length = 16)
	private String activeUserLimitMode;
	@Column(name = "active_user_limit")
	private Long activeUserLimit;
	@Column(name = "active_employee_limit_mode", nullable = false, length = 16)
	private String activeEmployeeLimitMode;
	@Column(name = "active_employee_limit")
	private Long activeEmployeeLimit;
}
