/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.core.observability.listener.tracing;

import org.springframework.core.observability.event.Recording;
import org.springframework.core.observability.event.interval.IntervalEvent;
import org.springframework.core.observability.event.interval.IntervalHttpServerEvent;
import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.tracing.Tracer;
import org.springframework.core.observability.tracing.http.HttpServerHandler;
import org.springframework.core.observability.transport.http.HttpServerRequest;
import org.springframework.core.observability.transport.http.HttpServerResponse;

/**
 * {@link RecordingListener} that uses the Tracing API to record events for HTTP server
 * side.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class HttpServerTracingRecordingListener extends
		HttpTracingRecordingListener<HttpServerRequest, HttpServerResponse> implements TracingRecordingListener {

	/**
	 * Creates a new instance of {@link HttpServerTracingRecordingListener}.
	 *
	 * @param tracer tracer
	 * @param handler http server handler
	 */
	public HttpServerTracingRecordingListener(Tracer tracer, HttpServerHandler handler) {
		super(tracer, handler::handleReceive, handler::handleSend);
	}

	@Override
	public boolean isApplicable(Recording<?, ?> recording) {
		return recording.getEvent() instanceof IntervalHttpServerEvent;
	}

	@Override
	HttpServerRequest getRequest(IntervalEvent event) {
		IntervalHttpServerEvent serverEvent = (IntervalHttpServerEvent) event;
		return serverEvent.getRequest();
	}

	@Override
	String getRequestMethod(IntervalEvent event) {
		IntervalHttpServerEvent serverEvent = (IntervalHttpServerEvent) event;
		return serverEvent.getRequest().method();
	}

	@Override
	HttpServerResponse getResponse(IntervalEvent event) {
		IntervalHttpServerEvent serverEvent = (IntervalHttpServerEvent) event;
		return serverEvent.getResponse();
	}

}
