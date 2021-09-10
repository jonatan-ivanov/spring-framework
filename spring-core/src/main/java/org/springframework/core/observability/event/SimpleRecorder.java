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

package org.springframework.core.observability.event;

import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingDeque;

import org.springframework.core.log.LogAccessor;
import org.springframework.core.observability.event.instant.InstantEvent;
import org.springframework.core.observability.event.instant.InstantRecording;
import org.springframework.core.observability.event.instant.NoOpInstantRecording;
import org.springframework.core.observability.event.instant.SimpleInstantRecording;
import org.springframework.core.observability.event.interval.IntervalEvent;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.event.interval.NoOpIntervalRecording;
import org.springframework.core.observability.event.interval.SimpleIntervalRecording;
import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.time.Clock;

/**
 * Simple implementation of a {@link Recorder}.
 *
 * @author Jonatan Ivanov
 * @since 6.0.0
 * @param <T> context type
 */
public class SimpleRecorder<T> implements Recorder<T> {

	private static final LogAccessor log = new LogAccessor(SimpleRecorder.class);

	private final RecordingListener<T> listener;

	private final Clock clock;

	private volatile boolean enabled;

	private final ThreadLocal<IntervalRecording<T>> threadLocal = new ThreadLocal<>();

	private final Deque<IntervalRecording<T>> recordings = new LinkedBlockingDeque<>();

	/**
	 * Create a new {@link SimpleRecorder}.
	 *
	 * @param listener the listener that needs to be notified about the recordings
	 * @param clock the clock to be used
	 */
	public SimpleRecorder(RecordingListener<T> listener, Clock clock) {
		this.listener = listener;
		this.clock = clock;
		this.enabled = true;
	}

	@Override
	public IntervalRecording<T> recordingFor(IntervalEvent event) {
		IntervalRecording<T> recording = this.enabled
				? new SimpleIntervalRecording<>(event, this.listener, this.clock,
						this::remove)
				: new NoOpIntervalRecording<>();
		setCurrentRecording(recording);
		return recording;
	}

	@Override
	public InstantRecording recordingFor(InstantEvent event) {
		return this.enabled ? new SimpleInstantRecording(event, this.listener, this.clock)
				: new NoOpInstantRecording();
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	private void setCurrentRecording(IntervalRecording<T> recording) {
		IntervalRecording<T> old = this.threadLocal.get();
		if (old != null) {
			log.trace(() -> "Putting previous recording to stack [" + old + "]");
			this.recordings.addFirst(old);
		}
		this.threadLocal.set(recording);
	}

	/**
	 * Returns the current interval recording.
	 *
	 * @return currently stored recording
	 */
	@Override
	public IntervalRecording<T> getCurrentRecording() {
		return this.threadLocal.get();
	}

	/**
	 * Removes the current span from thread local and brings back the previous
	 * span to the current thread local.
	 */
	private void remove() {
		this.threadLocal.remove();
		if (this.recordings.isEmpty()) {
			return;
		}
		try {
			IntervalRecording<T> first = this.recordings.removeFirst();
			log.debug(() -> "Took recording [" + first + "] from thread local");
			this.threadLocal.set(first);
		}
		catch (NoSuchElementException ex) {
			log.trace(ex, () -> "Failed to remove a recording from the queue");
		}
	}
}
