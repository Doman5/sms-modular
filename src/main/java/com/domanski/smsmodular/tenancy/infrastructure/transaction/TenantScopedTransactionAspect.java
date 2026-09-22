package com.domanski.smsmodular.tenancy.infrastructure.transaction;

import com.domanski.smsmodular.tenancy.api.contract.TenantContext;
import com.domanski.smsmodular.tenancy.api.contract.TenantId;
import com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.aspectj.lang.reflect.MethodSignature;





@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Profile("!test")
public final class TenantScopedTransactionAspect {

	private final PlatformTransactionManager transactionManager;
	private final JdbcTemplate jdbcTemplate;

	public TenantScopedTransactionAspect(
		PlatformTransactionManager transactionManager,
		JdbcTemplate jdbcTemplate
	) {
		this.transactionManager = transactionManager;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Around("@annotation(com.domanski.smsmodular.tenancy.api.contract.TenantScopedTransactional)")
	public Object applyTenantTransaction(ProceedingJoinPoint joinPoint) {
		TenantScopedTransactional annotation = AnnotationUtils.findAnnotation(
			((MethodSignature) joinPoint.getSignature()).getMethod(),
			TenantScopedTransactional.class
		);
		if (annotation == null) {
			throw new IllegalStateException("Tenant-scoped transaction annotation is missing");
		}
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setReadOnly(annotation.readOnly());
		return template.execute(status -> {
			TenantId tenantId = TenantContext.require();
			setLocalTenant(tenantId);
			try {
				return joinPoint.proceed();
			} catch (RuntimeException | Error exception) {
				throw exception;
			} catch (Throwable exception) {
				throw new TenantScopedInvocationException(exception);
			}
		});
	}

	private void setLocalTenant(TenantId tenantId) {
		
		
		
		jdbcTemplate.queryForObject(
			"select set_config('app.tenant_id', ?, true)",
			String.class,
			tenantId.value().toString()
		);
	}

	static final class TenantScopedInvocationException extends RuntimeException {

		TenantScopedInvocationException(Throwable cause) {
			super("Tenant-scoped operation failed", cause);
		}
	}
}
