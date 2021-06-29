/*
 * Copyright 2012-2020 the original author or authors.
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

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import org.springframework.core.metrics.StartupStep;
import org.springframework.observability.tracing.Span;
import org.springframework.observability.tracing.internal.EncodingUtils;
import org.springframework.observability.tracing.internal.SpanNameUtil;

/**
 * {@link StartupStep} implementation for Spring Observability.
 *
 * @author Marcin Grzejszczak
 */
class ObservabilityStartupStep implements StartupStep {

	private final Span span;

	private final String name;

	public ObservabilityStartupStep(String name,
			Span span) {
		this.span = span.start().name(SpanNameUtil.toLowerHyphen(name(name))).event(name);
		this.name = name;
	}

	private String name(String name) {
		int index = name.lastIndexOf(".");
		if (index != -1) {
			return name.substring(index + 1);
		}
		return name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public long getId() {
		return EncodingUtils.longFromBase16String(this.span.context().spanId());
	}

	@Override
	public Long getParentId() {
		String parentId = this.span.context().parentId();
		if (parentId == null) {
			return null;
		}
		return EncodingUtils.longFromBase16String(parentId);
	}

	@Override
	public StartupStep tag(String key, String value) {
		if (key.equals("beanName")) {
			this.span.name(name(value));
		}
		this.span.tag(SpanNameUtil.toLowerHyphen(key), value);
		return this;
	}

	@Override
	public StartupStep tag(String key, Supplier<String> value) {
		this.span.tag(SpanNameUtil.toLowerHyphen(key), value.get());
		return this;
	}

	@Override
	public Tags getTags() {
		return Collections::emptyIterator;
	}

	@Override
	public void end() {
		this.span.end();
	}
}
