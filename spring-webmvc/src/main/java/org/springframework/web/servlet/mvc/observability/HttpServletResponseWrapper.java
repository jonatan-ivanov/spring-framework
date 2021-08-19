package org.springframework.web.servlet.mvc.observability;

import java.util.Collection;

import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.core.observability.transport.http.HttpServerResponse;

/**
 * Wrapper around the {@link HttpServletResponse}.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class HttpServletResponseWrapper implements HttpServerResponse {

	@Nullable
	private final HttpServletRequestWrapper request;

	private final HttpServletResponse response;

	@Nullable
	private final Throwable caught;

	public HttpServletResponseWrapper(@Nullable HttpServletRequest request, HttpServletResponse response,
			@Nullable Throwable caught) {
		if (response == null) {
			throw new NullPointerException("response == null");
		}
		this.request = request != null ? new HttpServletRequestWrapper(request) : null;
		this.response = response;
		this.caught = caught;
	}

	@Override
	public final Object unwrap() {
		return this.response;
	}

	@Override
	public Collection<String> headerNames() {
		return this.response.getHeaderNames();
	}

	@Override
	@Nullable
	public HttpServletRequestWrapper request() {
		return this.request;
	}

	@Override
	public Throwable error() {
		if (this.caught != null) {
			return this.caught;
		}
		if (this.request == null) {
			return null;
		}
		return this.request.maybeError();
	}

	@Override
	public int statusCode() {
		int result = this.response.getStatus();
		if (this.caught != null && result == 200) { // We may have a potentially bad status due
			// to defaults
			// Servlet only seems to define one exception that has a built-in code. Logic
			// in Jetty
			// defaults the status to 500 otherwise.
			if (this.caught instanceof UnavailableException) {
				return ((UnavailableException) this.caught).isPermanent() ? 404 : 503;
			}
			return 500;
		}
		return result;
	}

}
