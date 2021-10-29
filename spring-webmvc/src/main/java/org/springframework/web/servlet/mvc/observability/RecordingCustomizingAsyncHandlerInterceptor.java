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

package org.springframework.web.servlet.mvc.observability;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.micrometer.core.instrument.Timer;

import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Same as {@link RecordingCustomizingHandlerInterceptor} except it can be used as both an
 * {@link AsyncHandlerInterceptor} or a normal {@link HandlerInterceptor}.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
// TODO: IMO we will just need to mutate the request to contain attributes that later will be
// used to tag the span
public final class RecordingCustomizingAsyncHandlerInterceptor implements AsyncHandlerInterceptor, HandlerInterceptor {

	private final HandlerParser handlerParser;

	public RecordingCustomizingAsyncHandlerInterceptor(HandlerParser handlerParser) {
		this.handlerParser = handlerParser;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
		Object recording = request.getAttribute(Timer.Sample.class.getName());
		if (recording instanceof Timer.Sample) {
			this.handlerParser.preHandle(request, o, (Timer.Sample) recording);
		}
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		Object recording = request.getAttribute(Timer.Sample.class.getName());
		if (recording instanceof Timer.Sample) {
			this.handlerParser.postHandle(request, handler, modelAndView,
					(Timer.Sample) recording);
		}
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		Object recording = request.getAttribute(Timer.Sample.class.getName());
		if (recording instanceof Timer.Sample) {
			RecordingCustomizingHandlerInterceptor.setErrorAttribute(request, ex);
			RecordingCustomizingHandlerInterceptor.setHttpRouteAttribute(request);
		}
	}

}
