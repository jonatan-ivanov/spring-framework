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

package org.springframework.core.observability.instrumentation;

import org.springframework.core.observability.event.Recorder;
import org.springframework.core.observability.event.interval.IntervalEvent;
import org.springframework.core.observability.event.interval.IntervalRecording;

/**
 * An instrumented version of a Runnable.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class ContinuedRunnable implements Runnable {

	private final Recorder<?> recorder;

	private final IntervalRecording<?> recording;

	private final Runnable delegate;

	public ContinuedRunnable(Recorder<?> recorder, IntervalEvent intervalEvent, Runnable delegate) {
		this.recorder = recorder;
		this.recording = this.recorder.recordingFor(intervalEvent);
		this.delegate = delegate;
	}

	public ContinuedRunnable(Recorder<?> recorder, Runnable delegate) {
		this(recorder, () -> "async", delegate);
	}

	@Override
	public void run() {
		IntervalRecording<?> started = this.recording.restore();
		try {
			delegate.run();
		} catch (Exception ex) {
			started.error(ex);
			throw ex;
		} finally {
			started.stop();
		}
	}
}
