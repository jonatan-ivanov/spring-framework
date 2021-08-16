package org.springframework.web.servlet.mvc.observability;

import java.util.Collection;

import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.observability.core.http.HttpServerResponse;

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
		return response;
	}

	@Override
	public Collection<String> headerNames() {
		return this.response.getHeaderNames();
	}

	@Override
	@Nullable
	public HttpServletRequestWrapper request() {
		return request;
	}

	@Override
	public Throwable error() {
		if (caught != null) {
			return caught;
		}
		if (request == null) {
			return null;
		}
		return request.maybeError();
	}

	@Override
	public int statusCode() {
		int result = this.response.getStatus();
		if (caught != null && result == 200) { // We may have a potentially bad status due
			// to defaults
			// Servlet only seems to define one exception that has a built-in code. Logic
			// in Jetty
			// defaults the status to 500 otherwise.
			if (caught instanceof UnavailableException) {
				return ((UnavailableException) caught).isPermanent() ? 404 : 503;
			}
			return 500;
		}
		return result;
	}

}
