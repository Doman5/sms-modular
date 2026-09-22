package com.domanski.smsmodular.common.api;

import java.util.List;
import org.springframework.data.domain.Page;








public record PageResponse<T>(
	List<T> items,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public PageResponse {
		if (items == null) {
			throw new IllegalArgumentException("Page items are required");
		}
		items = List.copyOf(items);
		if (page < 0) {
			throw new IllegalArgumentException("Page index cannot be negative");
		}
		if (size <= 0) {
			throw new IllegalArgumentException("Page size must be positive");
		}
		if (totalElements < 0) {
			throw new IllegalArgumentException("Total elements cannot be negative");
		}
		if (totalPages < 0) {
			throw new IllegalArgumentException("Total pages cannot be negative");
		}
		long expectedTotalPages = totalElements == 0
			? 0
			: (totalElements + size - 1L) / size;
		if (expectedTotalPages > Integer.MAX_VALUE || totalPages != (int) expectedTotalPages) {
			throw new IllegalArgumentException("Total pages do not match total elements and page size");
		}
		if (totalElements == 0 && page != 0) {
			throw new IllegalArgumentException("An empty result must use page zero");
		}
		if (totalElements > 0 && page >= totalPages) {
			throw new IllegalArgumentException("Page index is outside the result set");
		}
		if (items.size() > size) {
			throw new IllegalArgumentException("Page contains more items than its page size");
		}
	}

	public static <T> PageResponse<T> from(Page<T> page) {
		if (page == null) {
			throw new IllegalArgumentException("Page is required");
		}
		return new PageResponse<>(
			page.getContent(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}

	public static <T> PageResponse<T> empty(int size) {
		return new PageResponse<>(List.of(), 0, size, 0, 0);
	}
}
