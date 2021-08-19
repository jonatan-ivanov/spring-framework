package org.springframework.web.servlet.mvc.observability;

import java.util.Collection;
import java.util.Collections;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;
import org.springframework.core.observability.transport.http.HttpServerRequest;

/**
 * Wrapper around the {@link HttpServletRequest}.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class HttpServletRequestWrapper implements HttpServerRequest {

	private final HttpServletRequest delegate;

	public HttpServletRequestWrapper(HttpServletRequest delegate) {
		this.delegate = delegate;
	}

	@Override
	public Object getAttribute(String key) {
		return this.delegate.getAttribute(key);
	}

	@Override
	public void setAttribute(String key, Object value) {
		this.delegate.setAttribute(key, value);
	}

	@Override
	public Collection<String> headerNames() {
		return Collections.list(this.delegate.getHeaderNames());
	}

	@Override
	public Object unwrap() {
		return this.delegate;
	}

	@Override
	public String method() {
		return this.delegate.getMethod();
	}

	@Override
	public String route() {
		Object maybeRoute = this.delegate.getAttribute("http.route");
		return maybeRoute instanceof String ? (String) maybeRoute : null;
	}

	@Override
	public String path() {
		return this.delegate.getRequestURI();
	}

	// not as some implementations may be able to do this more efficiently
	@Override
	public String url() {
		StringBuffer url = this.delegate.getRequestURL();
		if (this.delegate.getQueryString() != null && !this.delegate.getQueryString().isEmpty()) {
			url.append('?').append(this.delegate.getQueryString());
		}
		return url.toString();
	}

	@Override
	public String header(String name) {
		return this.delegate.getHeader(name);
	}

	/** Looks for a valid request attribute "error". */
	@Nullable
	Throwable maybeError() {
		Object maybeError = this.delegate.getAttribute("error");
		if (maybeError instanceof Throwable) {
			return (Throwable) maybeError;
		}
		maybeError = this.delegate.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
		if (maybeError instanceof Throwable) {
			return (Throwable) maybeError;
		}
		return null;
	}

	@Override
	public String remoteIp() {
		return this.delegate.getRemoteAddr();
	}

	@Override
	public int remotePort() {
		return this.delegate.getRemotePort();
	}
}
