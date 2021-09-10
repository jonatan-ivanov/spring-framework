/*
 * Copyright 2012-2021 the original author or authors.
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

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.micrometer.core.event.interval.IntervalHttpServerEvent;
import io.micrometer.core.event.interval.IntervalRecording;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.NestedServletException;

/**
 * Intercepts incoming HTTP requests handled by Spring MVC handlers and records events
 * about execution time and results.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 * @author Chanhyeong LEE
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class WebMvcObservabilityFilter extends OncePerRequestFilter {

	private static final Log logger = LogFactory.getLog(WebMvcObservabilityFilter.class);

	private final MeterRegistry meterRegistry;

	private final WebMvcTagsProvider tagsProvider;

	private final String metricName;

	/**
	 * Create a new {@link WebMvcObservabilityFilter} instance.
	 *
	 * @param recorder the meter recorder
	 * @param tagsProvider the tags provider
	 * @param metricName the metric name
	 */
	public WebMvcObservabilityFilter(MeterRegistry recorder,
			WebMvcTagsProvider tagsProvider, String metricName) {
		this.meterRegistry = recorder;
		this.tagsProvider = tagsProvider;
		this.metricName = metricName;
	}

	@Override
	protected boolean shouldNotFilterAsyncDispatch() {
		return false;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		TimingContext timingContext = TimingContext.get(request);
		if (timingContext == null) {
			timingContext = startAndAttachTimingContext(request);
		}
		try {
			filterChain.doFilter(request, response);
			if (!request.isAsyncStarted()) {
				// Only record when async processing has finished or never been started.
				// If async was started by something further down the chain we wait
				// until the second filter invocation (but we'll be using the
				// TimingContext that was attached to the first)
				Throwable exception = fetchException(request);
				record(timingContext, request, response, exception);
			}
		}
		catch (NestedServletException ex) {
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
			record(timingContext, request, response, ex.getCause());
			throw ex;
		}
		catch (ServletException | IOException | RuntimeException ex) {
			record(timingContext, request, response, ex);
			throw ex;
		}
	}

	private TimingContext startAndAttachTimingContext(HttpServletRequest request) {
		IntervalHttpServerEvent event = event(request);
		Timer.Sample recording = this.meterRegistry.timer(
				event.getLowCardinalityName()).toSample(event);
		TimingContext timingContext = new TimingContext(recording);
		timingContext.attachTo(request);
		return timingContext;
	}

	/**
	 * TODO: Can be overridden to support long task timing
	 * @param request request
	 * @return a HTTP server event
	 */
	@NotNull
	public IntervalHttpServerEvent event(HttpServletRequest request) {
		return new IntervalHttpServerEvent(new HttpServletRequestWrapper(request)) {
			@Override
			public String getLowCardinalityName() {
				return WebMvcObservabilityFilter.this.metricName;
			}

			@Override
			public String getDescription() {
				return "HTTP server recording";
			}
		};
	}

	private Throwable fetchException(HttpServletRequest request) {
		// Throwable exception = (Throwable) request.getAttribute(ErrorAttributes.ERROR_ATTRIBUTE);
		return (Throwable) request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE);
	}

	private void record(TimingContext timingContext, HttpServletRequest request, HttpServletResponse response,
			Throwable exception) {
		try {
			Object handler = getHandler(request);
			timingContext.getEvent().setHandler(handler);
			HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(request, response, exception);
			timingContext.getEvent().setResponse(responseWrapper);
			Timer.Sample sample = timingContext.getSample();
			this.tagsProvider.getTags(new HttpServletRequestWrapper(request),
					responseWrapper, handler, exception).forEach(sample::tag);
			sample.stop();
		}
		catch (Exception ex) {
			logger.warn("Failed to process the recording", ex);
			// Allow request-response exchange to continue, unaffected by recording problem
		}
	}

	private Object getHandler(HttpServletRequest request) {
		return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
	}

	/**
	 * Context object attached to a request to retain information across the multiple
	 * filter calls that happen with async requests.
	 */
	private static class TimingContext {

		private static final String ATTRIBUTE = TimingContext.class.getName();

		private static final String RECORDING_ATTRIBUTE = IntervalRecording.class.getName();

		private final Timer.Sample sample;

		TimingContext(Timer.Sample recording) {
			this.sample = recording;
		}

		Timer.Sample getSample() {
			return this.sample;
		}

		IntervalHttpServerEvent getEvent() {
			return (IntervalHttpServerEvent) this.sample.getEvent();
		}

		void attachTo(HttpServletRequest request) {
			request.setAttribute(ATTRIBUTE, this);
			request.setAttribute(RECORDING_ATTRIBUTE, this.sample);
		}

		static TimingContext get(HttpServletRequest request) {
			return (TimingContext) request.getAttribute(ATTRIBUTE);
		}

	}

}
