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

	private final RecordingListener<T> listener;

	private final Clock clock;

	private volatile boolean enabled;

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
		return this.enabled ? new SimpleIntervalRecording<>(event, this.listener, this.clock) : new NoOpIntervalRecording<>();
	}

	@Override
	public InstantRecording recordingFor(InstantEvent event) {
		return this.enabled ? new SimpleInstantRecording(event, this.listener, this.clock) : new NoOpInstantRecording();
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
