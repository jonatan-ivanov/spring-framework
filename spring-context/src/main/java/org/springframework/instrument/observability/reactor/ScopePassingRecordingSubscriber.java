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

package org.springframework.instrument.observability.reactor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Scannable;
import reactor.util.context.Context;

import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.lang.Nullable;

/**
 * A {@link Subscriber} that always restores a recording.
 *
 * @param <T> subscription type
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
final class ScopePassingRecordingSubscriber<T> implements RecordingSubscription<T>, Scannable {

	private static final Log log = LogFactory.getLog(ScopePassingRecordingSubscriber.class);

	private final Subscriber<? super T> subscriber;

	private final Context context;

	final IntervalRecording<?> parent;

	private Subscription s;

	ScopePassingRecordingSubscriber(Subscriber<? super T> subscriber, Context ctx,
			@Nullable IntervalRecording<?> parent) {
		this.subscriber = subscriber;
		this.parent = parent;
		Context context = parent != null
				&& !parent.equals(ctx.getOrDefault(IntervalRecording.class, null))
						? ctx.put(IntervalRecording.class, parent)
						: ctx;
		this.context = ReactorObservability.wrapContext(context);
		if (log.isTraceEnabled()) {
			log.trace("Parent span [" + parent + "], context [" + this.context + "]");
		}
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		restoreParentIfPresent();
		this.subscriber.onSubscribe(this);
	}

	/**
	 * 
	 */
	private void restoreParentIfPresent() {
		if (this.parent != null) {
			this.parent.restore();
		}
	}

	@Override
	public void request(long n) {
		restoreParentIfPresent();
		this.s.request(n);
	}

	@Override
	public void cancel() {
		restoreParentIfPresent();
		this.s.cancel();
	}

	@Override
	public void onNext(T o) {
		restoreParentIfPresent();
		this.subscriber.onNext(o);
	}

	@Override
	public void onError(Throwable throwable) {
		restoreParentIfPresent();
		this.subscriber.onError(throwable);
	}

	@Override
	public void onComplete() {
		restoreParentIfPresent();
		this.subscriber.onComplete();
	}

	@Override
	public Context currentContext() {
		return this.context;
	}

	@reactor.util.annotation.Nullable
	@Override
	public Object scanUnsafe(Attr key) {
		if (key == Attr.PARENT) {
			return this.s;
		}
		else if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}
		else {
			return key == Attr.ACTUAL ? this.subscriber : null;
		}
	}

	@Override
	public String toString() {
		return "ScopePassingSpanSubscriber{" + "subscriber=" + this.subscriber
				+ ", parent=" + this.parent + "}";
	}

}
