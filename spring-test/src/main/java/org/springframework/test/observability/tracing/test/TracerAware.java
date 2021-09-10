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

package org.springframework.test.observability.tracing.test;

import io.micrometer.core.instrument.tracing.CurrentTraceContext;
import io.micrometer.core.instrument.tracing.Tracer;
import io.micrometer.core.instrument.tracing.http.HttpClientHandler;
import io.micrometer.core.instrument.tracing.http.HttpRequestParser;
import io.micrometer.core.instrument.tracing.http.HttpServerHandler;
import io.micrometer.core.instrument.tracing.propagation.Propagator;

/**
 * Abstraction that provides all the necessary tracing components.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface TracerAware {

	/**
	 * Returns a {@link Tracer}.
	 *
	 * @return a {@link Tracer}
	 */
	Tracer tracer();

	/**
	 * Sets a tracing sampler.
	 *
	 * @param sampler tracing sampler
	 * @return this
	 */
	TracerAware sampler(TraceSampler sampler);

	/**
	 * Returns the {@link CurrentTraceContext}.
	 *
	 * @return a {@link CurrentTraceContext}
	 */
	CurrentTraceContext currentTraceContext();

	/**
	 * Returns the {@link Propagator}.
	 *
	 * @return a {@link Propagator}
	 */
	Propagator propagator();

	/**
	 * Returns the {@link HttpServerHandler}.
	 *
	 * @return a {@link HttpServerHandler}
	 */
	HttpServerHandler httpServerHandler();

	/**
	 * Sets a http request parser.
	 *
	 * @param httpRequestParser a {@link HttpRequestParser}
	 * @return this
	 */
	TracerAware clientRequestParser(HttpRequestParser httpRequestParser);

	/**
	 * Returns the {@link HttpClientHandler}.
	 *
	 * @return a {@link HttpClientHandler}
	 */
	HttpClientHandler httpClientHandler();

	/**
	 * Simple tracing sampler.
	 */
	enum TraceSampler {

		/**
		 * Always sampler.
		 */
		ON,

		/**
		 * Never sampler.
		 */
		OFF

	}

}
