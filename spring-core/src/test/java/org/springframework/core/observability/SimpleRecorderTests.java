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

package org.springframework.core.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.observability.event.SimpleRecorder;
import org.springframework.core.observability.event.instant.InstantRecording;
import org.springframework.core.observability.event.instant.NoOpInstantRecording;
import org.springframework.core.observability.event.instant.SimpleInstantRecording;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.event.interval.NoOpIntervalRecording;
import org.springframework.core.observability.event.interval.SimpleIntervalRecording;
import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.observability.test.TestInstantEvent.INSTANT_EVENT;
import static org.springframework.core.observability.test.TestIntervalEvent.INTERVAL_EVENT;

/**
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class SimpleRecorderTests {

	@Mock
	private RecordingListener<Void> listener;

	@Mock
	private Clock clock;

	@InjectMocks
	private SimpleRecorder<Void> recorder;

	@Test
	void shouldReturnSimpleIntervalRecordingByDefault() {
		IntervalRecording<Void> recording = this.recorder.recordingFor(INTERVAL_EVENT);
		assertThat(this.recorder.isEnabled()).isTrue();
		assertThat(recording).isExactlyInstanceOf(SimpleIntervalRecording.class);
		assertThat(recording.getEvent()).isSameAs(INTERVAL_EVENT);
	}

	@Test
	void shouldReturnSimpleInstantRecordingByDefault() {
		InstantRecording recording = this.recorder.recordingFor(INSTANT_EVENT);
		assertThat(this.recorder.isEnabled()).isTrue();
		assertThat(recording).isExactlyInstanceOf(SimpleInstantRecording.class);
		assertThat(recording.getEvent()).isSameAs(INSTANT_EVENT);
	}

	@Test
	void shouldReturnNoOpIntervalRecordingIfDisabled() {
		assertThat(this.recorder.isEnabled()).isTrue();
		assertThat(this.recorder.recordingFor(INTERVAL_EVENT)).isExactlyInstanceOf(SimpleIntervalRecording.class);

		this.recorder.setEnabled(false);
		assertThat(this.recorder.isEnabled()).isFalse();
		assertThat(this.recorder.recordingFor(INTERVAL_EVENT)).isExactlyInstanceOf(NoOpIntervalRecording.class);

		this.recorder.setEnabled(true);
		assertThat(this.recorder.isEnabled()).isTrue();
		assertThat(this.recorder.recordingFor(INTERVAL_EVENT)).isExactlyInstanceOf(SimpleIntervalRecording.class);
	}

	@Test
	void shouldReturnSimpleInstantRecordingIfDisabled() {
		assertThat(this.recorder.isEnabled()).isTrue();
		assertThat(this.recorder.recordingFor(INSTANT_EVENT)).isExactlyInstanceOf(SimpleInstantRecording.class);

		this.recorder.setEnabled(false);
		assertThat(this.recorder.isEnabled()).isFalse();
		assertThat(this.recorder.recordingFor(INSTANT_EVENT)).isExactlyInstanceOf(NoOpInstantRecording.class);

		this.recorder.setEnabled(true);
		assertThat(this.recorder.isEnabled()).isTrue();
		assertThat(this.recorder.recordingFor(INSTANT_EVENT)).isExactlyInstanceOf(SimpleInstantRecording.class);
	}

}
