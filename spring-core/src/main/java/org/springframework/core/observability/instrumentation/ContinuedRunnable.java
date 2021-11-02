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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * An instrumented version of a Runnable.
 *
 * @author Marcin Grzejszczak
 * @since 6.0.0
 */
public class ContinuedRunnable implements Runnable {

	private final MeterRegistry recorder;

	private final Timer.Sample recording;

	private final Runnable delegate;

	public ContinuedRunnable(MeterRegistry recorder, Timer.Context intervalEvent, Runnable delegate) {
		this.recorder = recorder;
		this.recording = recorder.getCurrentSample();
		this.delegate = delegate;
	}

	public ContinuedRunnable(MeterRegistry recorder, Runnable delegate) {
		this(recorder, new Timer.Context() {}, delegate);
	}

	@Override
	public void run() {
		if (recording == null) {
			delegate.run();
			return;
		}

		this.recording.restore();
		try {
			delegate.run();
		} catch (Exception ex) {
			recording.error(ex);
			throw ex;
		} finally {
			recording.stop(recorder.timer("async"));
		}
	}
}
