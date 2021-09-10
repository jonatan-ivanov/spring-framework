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

package org.springframework.test.observability.tracing.test.simple;

import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.http.HttpServerHandler;
import io.micrometer.core.instrument.transport.http.HttpServerRequest;
import io.micrometer.core.instrument.transport.http.HttpServerResponse;

/**
 * A test http server handler implementation.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class SimpleHttpServerHandler implements HttpServerHandler {

	private final SimpleTracer simpleTracer;

	/**
	 * Was the handle receive method called?
	 */
	public boolean receiveHandled;

	/**
	 * Creates a new instance.
	 *
	 * @param simpleTracer simple tracer
	 */
	public SimpleHttpServerHandler(SimpleTracer simpleTracer) {
		this.simpleTracer = simpleTracer;
	}

	@Override
	public Span handleReceive(HttpServerRequest request) {
		return this.simpleTracer.nextSpan().start();
	}

	@Override
	public void handleSend(HttpServerResponse response, Span span) {
		span.end();
		this.receiveHandled = true;
	}

}
