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

package org.springframework.web.servlet.mvc.observability;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.core.observability.transport.http.HttpServerRequest;
import org.springframework.core.observability.transport.http.HttpServerResponse;
import org.springframework.core.observability.event.tag.Cardinality;
import org.springframework.core.observability.event.tag.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * Factory methods for {@link Tag Tags} associated with a request-response exchange that
 * is handled by Spring MVC.
 *
 * @author Jon Schneider
 * @author Andy Wilkinson
 * @author Brian Clozel
 * @author Michael McFadyen
 * @since 6.0.0
 */
public final class WebMvcTags {

	private static final String DATA_REST_PATH_PATTERN_ATTRIBUTE = "org.springframework.data.rest.webmvc.RepositoryRestHandlerMapping.EFFECTIVE_REPOSITORY_RESOURCE_LOOKUP_PATH";

	private static final Tag URI_NOT_FOUND = Tag.of("uri", "NOT_FOUND", Cardinality.LOW);

	private static final Tag URI_REDIRECTION = Tag.of("uri", "REDIRECTION", Cardinality.LOW);

	private static final Tag URI_ROOT = Tag.of("uri", "root", Cardinality.LOW);

	private static final Tag URI_UNKNOWN = Tag.of("uri", "UNKNOWN", Cardinality.LOW);

	private static final Tag EXCEPTION_NONE = Tag.of("error", "None", Cardinality.LOW);

	private static final Tag STATUS_UNKNOWN = Tag.of("status", "UNKNOWN", Cardinality.LOW);

	private static final Tag METHOD_UNKNOWN = Tag.of("method", "UNKNOWN", Cardinality.LOW);

	private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/$");

	private static final Pattern MULTIPLE_SLASH_PATTERN = Pattern.compile("//+");

	private WebMvcTags() {
	}

	/**
	 * Creates a {@code method} tag based on the {@link HttpServerRequest#method()} ()
	 * method} of the given {@code request}.
	 * @param request the request
	 * @return the method tag whose value is a capitalized method (e.g. GET).
	 */
	public static Tag method(HttpServerRequest request) {
		return (request != null) ? Tag.of("http.method", request.method(), Cardinality.LOW) : METHOD_UNKNOWN;
	}

	/**
	 * Creates a {@code method} tag based on the {@link HttpServerRequest#method()} ()
	 * method} of the given {@code request}.
	 * @param className class name
	 * @return the method tag whose value is a capitalized method (e.g. GET).
	 */
	public static Tag controllerClass(String className) {
		return Tag.of("mvc.controller.class", className, Cardinality.HIGH);
	}

	/**
	 * Creates a {@code method} tag based on the {@link HttpServerRequest#method()} ()
	 * method} of the given {@code request}.
	 * @param methodName method name
	 * @return the method tag whose value is a capitalized method (e.g. GET).
	 */
	public static Tag controllerMethod(String methodName) {
		return Tag.of("mvc.controller.method", methodName, Cardinality.HIGH);
	}

	/**
	 * Creates a {@code status} tag based on the status of the given {@code response}.
	 * @param response the HTTP response
	 * @return the status tag derived from the status of the response
	 */
	public static Tag status(HttpServerResponse response) {
		return (response != null) ? Tag.of("http.status_code", Integer.toString(response.statusCode()), Cardinality.HIGH) : STATUS_UNKNOWN;
	}

	/**
	 * Creates a {@code uri} tag based on the URI of the given {@code request}. Uses the
	 * {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} best matching pattern if
	 * available. Falling back to {@code REDIRECTION} for 3xx responses, {@code NOT_FOUND}
	 * for 404 responses, {@code root} for requests with no path info, and {@code UNKNOWN}
	 * for all other requests.
	 * @param request the request
	 * @param response the response
	 * @return the uri tag derived from the request
	 */
	public static Tag uri(HttpServerRequest request, HttpServerResponse response) {
		return uri(request, response, false);
	}

	/**
	 * Creates a {@code uri} tag based on the URI of the given {@code request}. Uses the
	 * {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} best matching pattern if
	 * available. Falling back to {@code REDIRECTION} for 3xx responses, {@code NOT_FOUND}
	 * for 404 responses, {@code root} for requests with no path info, and {@code UNKNOWN}
	 * for all other requests.
	 * @param request the request
	 * @param response the response
	 * @param ignoreTrailingSlash whether to ignore the trailing slash
	 * @return the uri tag derived from the request
	 */
	public static Tag uri(HttpServerRequest request, HttpServerResponse response, boolean ignoreTrailingSlash) {
		if (request != null) {
			String pattern = getMatchingPattern(request);
			if (pattern != null) {
				if (ignoreTrailingSlash && pattern.length() > 1) {
					pattern = TRAILING_SLASH_PATTERN.matcher(pattern).replaceAll("");
				}
				if (pattern.isEmpty()) {
					return URI_ROOT;
				}
				return Tag.of("http.uri", pattern, Cardinality.HIGH);
			}
			if (response != null) {
				HttpStatus status = extractStatus(response);
				if (status != null) {
					if (status.is3xxRedirection()) {
						return URI_REDIRECTION;
					}
					if (status == HttpStatus.NOT_FOUND) {
						return URI_NOT_FOUND;
					}
				}
			}
			String pathInfo = getPathInfo(request);
			if (pathInfo.isEmpty()) {
				return URI_ROOT;
			}
		}
		return URI_UNKNOWN;
	}

	private static HttpStatus extractStatus(HttpServerResponse response) {
		try {
			return HttpStatus.valueOf(response.statusCode());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static String getMatchingPattern(HttpServerRequest request) {
		PathPattern dataRestPathPattern = (PathPattern) request.getAttribute(DATA_REST_PATH_PATTERN_ATTRIBUTE);
		if (dataRestPathPattern != null) {
			return dataRestPathPattern.getPatternString();
		}
		return (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
	}

	private static String getPathInfo(HttpServerRequest request) {
		String pathInfo = request.path();
		String uri = StringUtils.hasText(pathInfo) ? pathInfo : "/";
		uri = MULTIPLE_SLASH_PATTERN.matcher(uri).replaceAll("/");
		return TRAILING_SLASH_PATTERN.matcher(uri).replaceAll("");
	}

	/**
	 * Creates an {@code exception} tag based on the {@link Class#getSimpleName() simple
	 * name} of the class of the given {@code exception}.
	 * @param exception the exception, may be {@code null}
	 * @return the exception tag derived from the exception
	 */
	public static Tag exception(Throwable exception) {
		if (exception != null) {
			String simpleName = exception.getClass().getSimpleName();
			return Tag.of("error", StringUtils.hasText(simpleName) ? simpleName : exception.getClass().getName(), Cardinality.HIGH);
		}
		return EXCEPTION_NONE;
	}

}
