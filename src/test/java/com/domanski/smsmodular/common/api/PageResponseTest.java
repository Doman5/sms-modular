package com.domanski.smsmodular.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

	@Test
	void createsImmutablePageWithConsistentMetadata() {
		List<String> source = new ArrayList<>(List.of("one", "two"));
		PageResponse<String> response = new PageResponse<>(source, 0, 2, 3, 2);
		source.add("three");

		assertThat(response.items()).containsExactly("one", "two");
		assertThatThrownBy(() -> response.items().add("three"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void mapsSpringPageAndSupportsEmptyFirstPage() {
		var springPage = new PageImpl<>(List.of("one"), PageRequest.of(0, 2), 3);
		assertThat(PageResponse.from(springPage))
			.isEqualTo(new PageResponse<>(List.of("one"), 0, 2, 3, 2));
		assertThat(PageResponse.empty(25).totalPages()).isZero();
	}

	@Test
	void rejectsContradictoryOrUnsafeMetadata() {
		assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 10, 10, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PageResponse<>(List.of(), 1, 10, 0, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PageResponse<>(List.of("one"), 0, 0, 1, 1))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
