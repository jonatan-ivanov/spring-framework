package org.springframework.web.client.observability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.core.observability.transport.http.HttpClientResponse;
import org.springframework.core.observability.transport.http.HttpRequest;
import org.springframework.core.observability.event.tag.Cardinality;
import org.springframework.core.observability.event.tag.Tag;

public class DefaultObservabilityClientHttpRequestInterceptorTagsProvider implements ObservabilityClientHttpRequestInterceptorTagsProvider {

	@Override
	public Iterable<Tag> getTags(String urlTemplate, HttpRequest request, HttpClientResponse response) {
		List<Tag> tags = new ArrayList<>(Arrays.asList(
				Tag.of("http.method", request.method(), Cardinality.LOW),
				Tag.of("http.status_code", String.valueOf(response.statusCode()), Cardinality.LOW))
		);
		if (request.path() != null) {
			tags.add(Tag.of("http.path", request.path(), Cardinality.LOW));
		}
		return tags;
	};
}