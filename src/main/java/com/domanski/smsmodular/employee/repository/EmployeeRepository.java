package com.domanski.smsmodular.employee.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.domanski.smsmodular.employee.entity.Employee;
import com.domanski.smsmodular.employee.entity.EmployeeStatus;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
	Optional<Employee> findByTenantIdAndId(UUID tenantId, UUID id);
	Optional<Employee> findByTenantIdAndNormalizedPhone(UUID tenantId, String normalizedPhone);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from Employee e where e.tenantId = :tenantId and e.id = :id")
	Optional<Employee> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
	boolean existsByTenantIdAndNormalizedPhoneAndIdNot(UUID tenantId, String normalizedPhone, UUID id);
	long countByTenantIdAndStatus(UUID tenantId, EmployeeStatus status);

	@Query("select e from Employee e where e.tenantId = :tenantId "
			+ "and (:status is null or e.status = :status) "
			+ "and (:position is null or e.position = :position) "
			+ "and (:search = '' or lower(concat(e.firstName, ' ', e.lastName)) like concat('%', :search, '%') "
			+ "or lower(e.email) like concat('%', :search, '%') "
			+ "or lower(e.position) like concat('%', :search, '%') "
			+ "or (:phone <> '' and e.normalizedPhone like concat('%', :phone, '%')) "
			+ "or e.phoneDisplay like concat('%', :search, '%'))")
	Page<Employee> search(@Param("tenantId") UUID tenantId, @Param("status") EmployeeStatus status,
			@Param("position") String position, @Param("search") String search,
			@Param("phone") String phone, Pageable pageable);

	@Query("select distinct e.position from Employee e where e.tenantId = :tenantId order by e.position")
	List<String> positions(@Param("tenantId") UUID tenantId);
}
