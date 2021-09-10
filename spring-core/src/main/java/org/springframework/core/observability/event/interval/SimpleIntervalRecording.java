/*
 * Copyright 2021-2021 the original author or authors.
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

package org.springframework.core.observability.event.interval;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.log.LogAccessor;
import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.event.tag.Tag;
import org.springframework.core.observability.time.Clock;

/**
 * A simple implementation of {@link IntervalRecording}.
 *
 * @author Jonatan Ivanov
 * @since 6.0.0
 * @param <T> context type
 */
public class SimpleIntervalRecording<T> implements IntervalRecording<T> {

	private static final LogAccessor log = new LogAccessor(SimpleIntervalRecording.class);

	private final IntervalEvent event;

	private final RecordingListener<T> listener;

	private final T context;

	private final Clock clock;

	private final Runnable closingCallback;

	private final Set<Tag> tags = new LinkedHashSet<>();

	private String highCardinalityName;

	private Duration duration = Duration.ZERO;

	private long started = 0;

	private long stopped = 0;

	private long startWallTime = 0;

	private Throwable error = null;

	/**
	 * Creates a new instance of {@link SimpleIntervalRecording}.
	 *
	 * @param event the event this recording belongs to
	 * @param listener the listener that needs to be notified about the
	 * recordings
	 * @param clock the clock to be used
	 * @param closingCallback callback to be called upon closing of the
	 * recording
	 */
	public SimpleIntervalRecording(IntervalEvent event, RecordingListener<T> listener,
			Clock clock, Runnable closingCallback) {
		this.event = event;
		this.highCardinalityName = event.getLowCardinalityName();
		this.listener = listener;
		this.context = listener.createContext();
		this.clock = clock;
		this.listener.onCreate(this);
		this.closingCallback = closingCallback;
	}
	
	/**
	 * Creates a new instance of {@link SimpleIntervalRecording}.
	 *
	 * @param event the event this recording belongs to
	 * @param listener the listener that needs to be notified about the recordings
	 * @param clock the clock to be used
	 */
	SimpleIntervalRecording(IntervalEvent event, RecordingListener<T> listener,
			Clock clock) {
		this(event, listener, clock, () -> {
		});
	}

	@Override
	public IntervalEvent getEvent() {
		return this.event;
	}

	@Override
	public String getHighCardinalityName() {
		return this.highCardinalityName;
	}

	@Override
	public IntervalRecording<T> highCardinalityName(String highCardinalityName) {
		this.highCardinalityName = highCardinalityName;
		return this;
	}

	@Override
	public Duration getDuration() {
		return this.duration;
	}

	@Override
	public long getStartNanos() {
		return this.started;
	}

	@Override
	public IntervalRecording<T> start() {
		return start(this.clock.wallTime(), this.clock.monotonicTime());
	}

	@Override
	public IntervalRecording<T> restore() {
		this.listener.onRestore(this);
		return this;
	}

	@Override
	public IntervalRecording<T> start(long wallTime, long monotonicTime) {
		if (this.started != 0) {
			log.trace(() -> "IntervalRecording has already been started");
		}
		this.startWallTime = wallTime;
		this.started = monotonicTime;
		this.listener.onStart(this);
		return this;
	}

	@Override
	public long getStopNanos() {
		return this.stopped;
	}

	@Override
	public long getStartWallTime() {
		return this.startWallTime;
	}

	@Override
	public void stop() {
		stop(this.clock.monotonicTime());
	}

	@Override
	public void stop(long monotonicTime) {
		verifyIfHasStarted();
		verifyIfHasNotStopped();
		this.stopped = monotonicTime;
		this.duration = Duration.ofNanos(this.stopped - this.started);
		this.listener.onStop(this);
	}

	@Override
	public Iterable<Tag> getTags() {
		return Collections.unmodifiableSet(this.tags);
	}

	@Override
	public IntervalRecording<T> tag(Tag tag) {
		verifyIfHasNotStopped();
		this.tags.add(tag);
		return this;
	}

	@Override
	public Throwable getError() {
		return this.error;
	}

	@Override
	public IntervalRecording<T> error(Throwable error) {
		verifyIfHasStarted();
		verifyIfHasNotStopped();
		if (this.error != null) {
			log.trace(() -> "Only one error can be attached");
			return this;
		}

		this.error = error;
		this.listener.onError(this);
		return this;
	}

	@Override
	public T getContext() {
		return this.context;
	}

	@Override
	public String toString() {
		return "{" + "event=" + this.event.getLowCardinalityName() + ", highCardinalityName=" + this.highCardinalityName
				+ ", duration=" + this.duration.toMillis() + "ms" + ", tags=" + this.tags + ", error=" + this.error + '}';
	}

	private void verifyIfHasStarted() {
		if (this.started == 0) {
			log.trace(() -> "IntervalRecording hasn't been started");
		}
	}

	private void verifyIfHasNotStopped() {
		if (this.stopped != 0) {
			log.trace(() -> "IntervalRecording hasn't been stopped");
		}
	}

}
