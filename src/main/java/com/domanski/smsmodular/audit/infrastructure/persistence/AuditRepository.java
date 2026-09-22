package com.domanski.smsmodular.audit.infrastructure.persistence;

import com.domanski.smsmodular.audit.application.AuditFilter;
import com.domanski.smsmodular.audit.domain.AuditEntry;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;









@Repository
@Profile("!test")
public class AuditRepository {

	private final AuditJpaRepository repository;
	private final CopyOnWriteArrayList<AuditEntry> testEntries;

	@Autowired
	public AuditRepository(AuditJpaRepository repository) {
		this.repository = repository;
		this.testEntries = null;
	}

	
	public AuditRepository() {
		this.repository = null;
		this.testEntries = new CopyOnWriteArrayList<>();
	}

	public AuditEntry save(AuditEntry entry) {
		if (testEntries != null) {
			testEntries.add(entry);
			return entry;
		}
		return repository.save(AuditEntity.from(entry)).toDomain();
	}

	public Page<AuditEntry> find(AuditFilter filter, Pageable pageable) {
		if (testEntries == null) {
			return repository.findAll(specification(filter), pageable).map(AuditEntity::toDomain);
		}
		List<AuditEntry> matching = testEntries.stream()
			.filter(entry -> matches(entry, filter))
			.sorted(Comparator.comparing(AuditEntry::occurredAt).reversed()
				.thenComparing(AuditEntry::id, Comparator.reverseOrder()))
			.toList();
		int from = Math.min((int) pageable.getOffset(), matching.size());
		int to = Math.min(from + pageable.getPageSize(), matching.size());
		return new PageImpl<>(new ArrayList<>(matching.subList(from, to)), pageable, matching.size());
	}

	public Optional<AuditEntry> findById(UUID id) {
		if (testEntries != null) {
			return testEntries.stream().filter(entry -> entry.id().equals(id)).findFirst();
		}
		return repository.findById(id).map(AuditEntity::toDomain);
	}

	private Specification<AuditEntity> specification(AuditFilter filter) {
		return (root, query, builder) -> {
			ArrayList<Predicate> predicates = new ArrayList<>();
			if (filter.tenantId() != null) {
				predicates.add(builder.equal(root.get("tenantId"), filter.tenantId().value()));
			}
			if (filter.occurredFrom() != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), filter.occurredFrom()));
			}
			if (filter.occurredTo() != null) {
				predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), filter.occurredTo()));
			}
			if (filter.actorType() != null && !filter.actorType().isBlank()) {
				predicates.add(builder.equal(root.get("actorType"), filter.actorType().trim().toUpperCase()));
			}
			if (filter.actorId() != null) {
				predicates.add(builder.equal(root.get("actorId"), filter.actorId()));
			}
			if (filter.module() != null && !filter.module().isBlank()) {
				predicates.add(builder.equal(root.get("module"), filter.module().trim().toUpperCase()));
			}
			if (filter.action() != null && !filter.action().isBlank()) {
				predicates.add(builder.equal(root.get("action"), filter.action().trim().toUpperCase()));
			}
			if (filter.subjectType() != null && !filter.subjectType().isBlank()) {
				predicates.add(builder.equal(root.get("subjectType"), filter.subjectType().trim().toUpperCase()));
			}
			if (filter.subjectId() != null) {
				predicates.add(builder.equal(root.get("subjectId"), filter.subjectId()));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private boolean matches(AuditEntry entry, AuditFilter filter) {
		return (filter.tenantId() == null || filter.tenantId().equals(entry.tenantId()))
			&& (filter.occurredFrom() == null || !entry.occurredAt().isBefore(filter.occurredFrom()))
			&& (filter.occurredTo() == null || !entry.occurredAt().isAfter(filter.occurredTo()))
			&& (filter.actorType() == null || filter.actorType().isBlank()
				|| filter.actorType().equalsIgnoreCase(entry.actor().type()))
			&& (filter.actorId() == null || filter.actorId().equals(entry.actor().id()))
			&& (filter.module() == null || filter.module().isBlank()
				|| filter.module().equalsIgnoreCase(entry.module()))
			&& (filter.action() == null || filter.action().isBlank()
				|| filter.action().equalsIgnoreCase(entry.action()))
			&& (filter.subjectType() == null || filter.subjectType().isBlank()
				|| filter.subjectType().equalsIgnoreCase(entry.subjectType()))
			&& (filter.subjectId() == null || filter.subjectId().equals(entry.subjectId()));
	}
}
