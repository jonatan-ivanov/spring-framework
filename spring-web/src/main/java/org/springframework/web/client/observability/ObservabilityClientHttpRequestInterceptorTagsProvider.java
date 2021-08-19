package org.springframework.web.client.observability;

import org.springframework.core.observability.transport.http.HttpClientResponse;
import org.springframework.core.observability.transport.http.HttpRequest;
import org.springframework.core.observability.event.tag.Tag;

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