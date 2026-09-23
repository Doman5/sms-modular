package com.domanski.smsmodular.entitlements.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.domanski.smsmodular.entitlements.entity.ModuleCatalog;

public interface ModuleCatalogRepository extends JpaRepository<ModuleCatalog, String> {
	List<ModuleCatalog> findAllByOrderByTypeAscKeyAsc();
}
