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

import io.micrometer.core.event.interval.IntervalRecording;
import io.micrometer.core.instrument.Timer;

import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Adds application-tier data to an existing http span via {@link HandlerParser}. This
 * also sets the request property "http.route" so that it can be used in naming the http
 * span.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class RecordingCustomizingHandlerInterceptor implements HandlerInterceptor {

	private final HandlerParser handlerParser;

	public RecordingCustomizingHandlerInterceptor(HandlerParser handlerParser) {
		this.handlerParser = handlerParser;
	}

	/**
	 * Parses the request and sets the "http.route".
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object o) {
		Object recording = request.getAttribute(Timer.Sample.class.getName());
		if (recording instanceof IntervalRecording) {
			setHttpRouteAttribute(request);
			this.handlerParser.preHandle(request, o, (Timer.Sample) recording);
		}
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		Object recording = request.getAttribute(IntervalRecording.class.getName());
		if (recording instanceof IntervalRecording) {
			this.handlerParser.postHandle(request, handler, modelAndView,
					(Timer.Sample) recording);
		}
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		Object recording = request.getAttribute(IntervalRecording.class.getName());
		if (recording instanceof IntervalRecording) {
			setErrorAttribute(request, ex);
		}
	}

	static void setErrorAttribute(HttpServletRequest request, @Nullable Exception ex) {
		if (ex != null && request.getAttribute("error") == null) {
			request.setAttribute("error", ex);
		}
	}

	static void setHttpRouteAttribute(HttpServletRequest request) {
		Object httpRoute = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		// TODO: Push to a constant
		request.setAttribute("http.route", httpRoute != null ? httpRoute.toString() : "");
	}

}
