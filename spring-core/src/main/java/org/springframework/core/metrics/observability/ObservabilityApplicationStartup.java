/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.core.metrics.observability;

import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.observability.tracing.ThreadLocalSpan;
import org.springframework.observability.tracing.Tracer;

/**
 * {@link ApplicationStartup} implementation for the Spring Observability.
 *
 * @author Marcin Grzejszczak
 * @since 6.0
 */
public class ObservabilityApplicationStartup implements ApplicationStartup {

	private final ThreadLocalSpan threadLocalSpan;

	public ObservabilityApplicationStartup(Tracer tracer) {
		this.threadLocalSpan = new ThreadLocalSpan(tracer);
	}

	@Override
	public StartupStep start(String name) {
		// TODO: Is this done on a single thread?
		return new ObservabilityStartupStep(name, this.threadLocalSpan.nextSpan());
	}

}
