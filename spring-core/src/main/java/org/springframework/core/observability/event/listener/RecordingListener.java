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

package org.springframework.core.observability.event.listener;

import org.springframework.core.observability.event.Recording;
import org.springframework.core.observability.event.context.ContextFactory;
import org.springframework.core.observability.event.instant.InstantEvent;
import org.springframework.core.observability.event.instant.InstantRecording;
import org.springframework.core.observability.event.interval.IntervalRecording;

/**
 * Implementing this interface of this interface make it possible to listen to ongoing or
 * competed {@link Recording Recordings}.
 *
 * @author Jonatan Ivanov
 * @since 6.0.0
 * @param <T> context type
 */
public interface RecordingListener<T> extends ContextFactory<T> {

	/**
	 * Defines whether this listener should be applied.
	 *
	 * @param recording recording
	 * @return {@code true} when this listener is applicable
	 */
	default boolean isApplicable(Recording<?, ?> recording) {
		return true;
	}

	/**
	 * Called after the recording was created and before it was started.
	 *
	 * @param intervalRecording the recording that was created
	 */
	void onCreate(IntervalRecording<T> intervalRecording);

	/**
	 * Called after the recording was started.
	 *
	 * @param intervalRecording the recording that was started
	 */
	void onStart(IntervalRecording<T> intervalRecording);

	/**
	 * Called after the recording was stopped.
	 *
	 * @param intervalRecording the recording that was stopped
	 */
	void onStop(IntervalRecording<T> intervalRecording);

	/**
	 * Called after a {@link Throwable} was thrown during the recording.
	 *
	 * @param intervalRecording the recording that was in progress when the error
	 * happened
	 */
	void onError(IntervalRecording<T> intervalRecording);

	/**
	 * Called when the recording is to be restored e.g. in a new thread.
	 *
	 * @param intervalRecording the recording that was started
	 */
	void onRestore(IntervalRecording<T> intervalRecording);

	/**
	 * Signals that an {@link InstantEvent} was recorded.
	 *
	 * @param instantRecording the recording that belongs to the recorded
	 * {@link InstantEvent}
	 */
	void record(InstantRecording instantRecording);

}
