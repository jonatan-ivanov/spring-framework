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

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Spring MVC specific type used to customize traced requests based on the handler.
 *
 * <p>
 * Note: This should not duplicate data. For example, this should not add the tag
 * "http.url".
 *
 * <p>
 * Tagging policy adopted from spring cloud sleuth 1.3.x
 */
//TODO: IMO we will just need to mutate the request to contain attributes that later will be
//used to tag the span
public class HandlerParser {

	/*
	 * Intentionally public for @Autowired to work without explicit binding
	 */
	public HandlerParser() {
	}

	/** Adds no tags to the span representing the request. */
	public static final HandlerParser NOOP = new HandlerParser() {
		@Override
		protected void preHandle(HttpServletRequest request, Object handler,
				Timer.Sample customizer) {
		}

		@Override
		protected void postHandle(HttpServletRequest request, Object handler, ModelAndView modelAndView,
				Timer.Sample customizer) {
		}
	};

	/**
	 * Invoked prior to request invocation during
	 * {@link HandlerInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}.
	 *
	 * <p>
	 * Adds the tags for controller class and method. Override or
	 * use {@link #NOOP} to change this behavior.
	 * @param request request
	 * @param handler handler
	 * @param customizer span customizer
	 */
	protected void preHandle(HttpServletRequest request, Object handler,
			Timer.Sample customizer) {
		if (handler instanceof HandlerMethod) {
			HandlerMethod handlerMethod = ((HandlerMethod) handler);
//			customizer.tag(WebMvcTags.controllerClass(handlerMethod.getBeanType().getSimpleName()));
//			customizer.tag(WebMvcTags.controllerMethod(handlerMethod.getMethod().getName()));
		}
		else {
//			customizer.tag(WebMvcTags.controllerClass(handler.getClass().getSimpleName()));
		}
	}

	/**
	 * Invoked posterior to request invocation during
	 * {@link HandlerInterceptor#postHandle(HttpServletRequest, HttpServletResponse, Object, ModelAndView)}.
	 * @param request request
	 * @param handler handler
	 * @param customizer span customizer
	 */
	protected void postHandle(HttpServletRequest request, Object handler, ModelAndView modelAndView,
			Timer.Sample customizer) {
	}

}
