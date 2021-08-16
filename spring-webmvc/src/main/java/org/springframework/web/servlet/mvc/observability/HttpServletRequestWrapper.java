package org.springframework.web.servlet.mvc.observability;

import java.util.Collection;
import java.util.Collections;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

import org.springframework.lang.Nullable;
import org.springframework.observability.core.http.HttpServerRequest;

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
		return delegate;
	}

	@Override
	public String method() {
		return delegate.getMethod();
	}

	@Override
	public String route() {
		Object maybeRoute = delegate.getAttribute("http.route");
		return maybeRoute instanceof String ? (String) maybeRoute : null;
	}

	@Override
	public String path() {
		return delegate.getRequestURI();
	}

	// not as some implementations may be able to do this more efficiently
	@Override
	public String url() {
		StringBuffer url = delegate.getRequestURL();
		if (delegate.getQueryString() != null && !delegate.getQueryString().isEmpty()) {
			url.append('?').append(delegate.getQueryString());
		}
		return url.toString();
	}

	@Override
	public String header(String name) {
		return delegate.getHeader(name);
	}

	/** Looks for a valid request attribute "error". */
	@Nullable
	Throwable maybeError() {
		Object maybeError = delegate.getAttribute("error");
		if (maybeError instanceof Throwable) {
			return (Throwable) maybeError;
		}
		maybeError = delegate.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
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
