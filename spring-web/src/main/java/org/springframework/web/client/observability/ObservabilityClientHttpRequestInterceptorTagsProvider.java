package org.springframework.web.client.observability;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;
import io.micrometer.core.instrument.transport.http.HttpRequest;

@FunctionalInterface
public interface ObservabilityClientHttpRequestInterceptorTagsProvider {

	/**
	 * Provides the tags to be associated with metrics that are recorded for the given
	 * {@code request} and {@code response} exchange.
	 * @param urlTemplate the source URl template, if available
	 * @param request the request
	 * @param response the response (may be {@code null} if the exchange failed)
	 * @return the tags
	 */
	Iterable<Tag> getTags(String urlTemplate, HttpRequest request, HttpClientResponse response);

}