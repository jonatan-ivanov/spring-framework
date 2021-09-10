/*
 * Copyright 2013-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.client.observability;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import io.micrometer.core.event.interval.IntervalHttpClientEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.transport.http.HttpClientRequest;
import io.micrometer.core.instrument.transport.http.HttpClientResponse;

import org.springframework.core.log.LogAccessor;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpStatusCodeException;

public final class ObservabilityClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

	private static final LogAccessor log = new LogAccessor(ObservabilityClientHttpRequestInterceptor.class);

	private final MeterRegistry recorder;

	private final ObservabilityClientHttpRequestInterceptorTagsProvider tagsProvider;

	public ObservabilityClientHttpRequestInterceptor(MeterRegistry recorder,
			ObservabilityClientHttpRequestInterceptorTagsProvider tagsProvider) {
		this.recorder = recorder;
		this.tagsProvider = tagsProvider;
	}

	@Override
	public ClientHttpResponse intercept(HttpRequest req, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		HttpRequestWrapper request = new HttpRequestWrapper(req);
		IntervalHttpClientEvent intervalEvent = new IntervalHttpClientEvent(request) {

			@Override
			public String getLowCardinalityName() {
				return "http.client.request";
			}

			@Override
			public String getDescription() {
				return "Wraps an outbound RestTemplate call";
			}
		};
		Timer.Sample intervalRecording = this.recorder.timer(
				intervalEvent.getLowCardinalityName()).toSample(intervalEvent);
		intervalRecording.start();
		log.debug(() -> "Started recording for rest template instrumentation");
		ClientHttpResponse response = null;
		Throwable error = null;
		try {
			response = execution.execute(req, body);
			return response;
		}
		catch (Throwable e) {
			error = e;
			intervalRecording.error(e);
			throw e;
		}
		finally {
			ClientHttpResponseWrapper wrapper = new ClientHttpResponseWrapper(request, response, error);
			this.tagsProvider.getTags(request.url(), request, wrapper).forEach(intervalRecording::tag);
			intervalEvent.setResponse(wrapper);
			intervalRecording.stop();
		}
	}

	static final class HttpRequestWrapper implements HttpClientRequest {

		final HttpRequest delegate;

		HttpRequestWrapper(HttpRequest delegate) {
			this.delegate = delegate;
		}

		@Override
		public Collection<String> headerNames() {
			return this.delegate.getHeaders().keySet();
		}

		@Override
		public Object unwrap() {
			return this.delegate;
		}

		@Override
		public String method() {
			return this.delegate.getMethod().name();
		}

		@Override
		public String path() {
			return this.delegate.getURI().getPath();
		}

		@Override
		public String url() {
			return this.delegate.getURI().toString();
		}

		@Override
		public String header(String name) {
			Object result = this.delegate.getHeaders().getFirst(name);
			return result != null ? result.toString() : null;
		}

		@Override
		public void header(String name, String value) {
			this.delegate.getHeaders().set(name, value);
		}

	}

	static final class ClientHttpResponseWrapper implements HttpClientResponse {

		final HttpRequestWrapper request;

		@Nullable
		final ClientHttpResponse response;

		@Nullable
		final Throwable error;

		ClientHttpResponseWrapper(HttpRequestWrapper request, @Nullable ClientHttpResponse response,
				@Nullable Throwable error) {
			this.request = request;
			this.response = response;
			this.error = error;
		}

		@Override
		public Object unwrap() {
			return this.response;
		}

		@Override
		public Collection<String> headerNames() {
			return this.response != null ? this.response.getHeaders().keySet() : Collections.emptyList();
		}

		@Override
		public HttpRequestWrapper request() {
			return this.request;
		}

		@Override
		public Throwable error() {
			return this.error;
		}

		@Override
		public int statusCode() {
			try {
				int result = this.response != null ? this.response.getRawStatusCode() : 0;
				if (result <= 0 && this.error instanceof HttpStatusCodeException) {
					result = ((HttpStatusCodeException) this.error).getRawStatusCode();
				}
				return result;
			}
			catch (Exception e) {
				return 0;
			}
		}

	}

}