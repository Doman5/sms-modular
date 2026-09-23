package com.domanski.smsmodular.identity.service;

import java.util.Set;

public final class PermissionCatalog {

	public static final String TENANT_READ = "TENANT_READ";
	public static final String TENANT_EDIT = "TENANT_EDIT";
	public static final String USER_READ = "USER_READ";
	public static final String USER_MANAGE = "USER_MANAGE";
	public static final String ROLE_READ = "ROLE_READ";
	public static final String ROLE_MANAGE = "ROLE_MANAGE";
	public static final String AUDIT_READ = "AUDIT_READ";
	public static final String SUBSCRIPTION_READ = "SUBSCRIPTION_READ";
	public static final String EMPLOYEE_READ = "EMPLOYEE_READ";
	public static final String EMPLOYEE_CREATE = "EMPLOYEE_CREATE";
	public static final String EMPLOYEE_EDIT = "EMPLOYEE_EDIT";
	public static final String EMPLOYEE_STATUS_CHANGE = "EMPLOYEE_STATUS_CHANGE";
	public static final String PLATFORM_TENANT_READ = "PLATFORM_TENANT_READ";
	public static final String PLATFORM_TENANT_MANAGE = "PLATFORM_TENANT_MANAGE";
	public static final String PLATFORM_AUDIT_READ = "PLATFORM_AUDIT_READ";
	public static final String PLATFORM_SUBSCRIPTION_READ = "PLATFORM_SUBSCRIPTION_READ";
	public static final String PLATFORM_SUBSCRIPTION_MANAGE = "PLATFORM_SUBSCRIPTION_MANAGE";

	public static final Set<String> TENANT_PERMISSIONS = Set.of(TENANT_READ, TENANT_EDIT, USER_READ,
			USER_MANAGE, ROLE_READ, ROLE_MANAGE, AUDIT_READ, SUBSCRIPTION_READ,
			EMPLOYEE_READ, EMPLOYEE_CREATE, EMPLOYEE_EDIT, EMPLOYEE_STATUS_CHANGE);

	private PermissionCatalog() {
	}
}
