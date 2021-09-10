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

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import io.micrometer.core.instrument.tracing.BaggageInScope;
import io.micrometer.core.instrument.tracing.CurrentTraceContext;
import io.micrometer.core.instrument.tracing.ScopedSpan;
import io.micrometer.core.instrument.tracing.Span;
import io.micrometer.core.instrument.tracing.SpanCustomizer;
import io.micrometer.core.instrument.tracing.TraceContext;
import io.micrometer.core.instrument.tracing.Tracer;

/**
 * A test tracer implementation. Puts started span in a list.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class SimpleTracer implements Tracer {

	private final CurrentTraceContext currentTraceContext;

	/**
	 * Recorded spans.
	 */
	public Deque<SimpleSpan> spans = new LinkedList<>();

	/**
	 * Creates a new instance.
	 */
	public SimpleTracer() {
		this.currentTraceContext = SimpleCurrentTraceContext.withTracer(this);
	}

	@Override
	public Span nextSpan(Span parent) {
		return new SimpleSpan();
	}

	/**
	 * Returns the only span.
	 *
	 * @return a single reported span
	 */
	public SimpleSpan getOnlySpan() {
		assertTrue(this.spans.size() == 1, "There must be only one span");
		SimpleSpan span = this.spans.getFirst();
		assertTrue(span.started, "Span must be started");
		assertTrue(span.ended, "Span must be finished");
		return span;
	}

	private void assertTrue(boolean condition, String text) {
		if (!condition) {
			throw new AssertionError(text);
		}
	}

	/**
	 * Returns the last span.
	 *
	 * @return the last reported span
	 */
	public SimpleSpan getLastSpan() {
		assertTrue(!this.spans.isEmpty(), "There must be at least one span");
		SimpleSpan span = this.spans.getLast();
		assertTrue(span.started, "Span must be started");
		return span;
	}

	@Override
	public SpanInScope withSpan(Span span) {
		return new SimpleSpanInScope();
	}

	@Override
	public SpanCustomizer currentSpanCustomizer() {
		return null;
	}

	@Override
	public Span currentSpan() {
		if (this.spans.isEmpty()) {
			return null;
		}
		return this.spans.getLast();
	}

	@Override
	public SimpleSpan nextSpan() {
		final SimpleSpan span = new SimpleSpan();
		this.spans.add(span);
		return span;
	}

	@Override
	public ScopedSpan startScopedSpan(String name) {
		return null;
	}

	@Override
	public Span.Builder spanBuilder() {
		return new SimpleSpanBuilder(this);
	}

	@Override
	public TraceContext.Builder traceContextBuilder() {
		return null;
	}

	@Override
	public CurrentTraceContext currentTraceContext() {
		return this.currentTraceContext;
	}

	@Override
	public Map<String, String> getAllBaggage() {
		return new HashMap<>();
	}

	@Override
	public BaggageInScope getBaggage(String name) {
		return null;
	}

	@Override
	public BaggageInScope getBaggage(TraceContext traceContext, String name) {
		return null;
	}

	@Override
	public BaggageInScope createBaggage(String name) {
		return null;
	}

	@Override
	public BaggageInScope createBaggage(String name, String value) {
		return null;
	}

}
