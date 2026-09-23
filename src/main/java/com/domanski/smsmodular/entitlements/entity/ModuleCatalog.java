package com.domanski.smsmodular.entitlements.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "module_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModuleCatalog {
	@Id
	@Column(name = "key", length = 64)
	private String key;
	@Column(nullable = false, length = 16)
	private String type;
	@Column(nullable = false, length = 16)
	private String status;
	@Column(name = "depends_on", length = 64)
	private String dependsOn;
}
