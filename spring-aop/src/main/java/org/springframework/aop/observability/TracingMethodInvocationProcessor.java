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

package org.springframework.aop.observability;

import org.aopalliance.intercept.MethodInvocation;

import org.springframework.core.observability.tracing.annotation.ContinueSpan;
import org.springframework.core.observability.tracing.annotation.NewSpan;

/**
 * Contract for processing tracing annotations.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public interface TracingMethodInvocationProcessor {

	/**
	 * Executes a given Sleuth annotated method.
	 *
	 * @param invocation method invocation
	 * @param newSpan annotation
	 * @param continueSpan annotation
	 * @return executed method result
	 * @throws Throwable exception upon running a method
	 */
	Object process(MethodInvocation invocation, NewSpan newSpan, ContinueSpan continueSpan) throws Throwable;

}
