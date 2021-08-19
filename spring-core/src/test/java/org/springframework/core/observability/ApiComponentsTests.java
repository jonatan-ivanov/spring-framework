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

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.observability.event.Recorder;
import org.springframework.core.observability.event.SimpleRecorder;
import org.springframework.core.observability.event.instant.InstantRecording;
import org.springframework.core.observability.event.instant.NoOpInstantRecording;
import org.springframework.core.observability.event.interval.IntervalEvent;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.event.interval.NoOpIntervalRecording;
import org.springframework.core.observability.event.listener.composite.AllMatchingCompositeRecordingListener;
import org.springframework.core.observability.event.listener.composite.CompositeContext;
import org.springframework.core.observability.event.tag.Cardinality;
import org.springframework.core.observability.event.tag.Tag;
import org.springframework.core.observability.test.TestContext;
import org.springframework.core.observability.test.TestRecordingListener;
import org.springframework.core.observability.time.Clock;
import org.springframework.core.observability.time.MockClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.observability.event.tag.Cardinality.HIGH;
import static org.springframework.core.observability.event.tag.Cardinality.LOW;
import static org.springframework.core.observability.test.TestInstantEvent.INSTANT_EVENT;
import static org.springframework.core.observability.test.TestIntervalEvent.INTERVAL_EVENT;

/**
 * @author Jonatan Ivanov
 */
class ApiComponentsTests {

	private final MockClock clock = new MockClock();

	private final TestRecordingListener listener = new TestRecordingListener(this.clock);

	private final Recorder<CompositeContext> recorder = new SimpleRecorder<>(
			new AllMatchingCompositeRecordingListener(this.listener), this.clock);

	@BeforeEach
	void setUp() {
		this.recorder.setEnabled(true);
		this.listener.reset();
	}

	@Test
	void shouldRecordInstantEvent() {
		this.recorder.recordingFor(INSTANT_EVENT).highCardinalityName(INSTANT_EVENT.getLowCardinalityName() + "-12345")
				.tag(Tag.of("testKey1", "testValue1", LOW)).tag(Tag.of("testKey2", "testValue2", HIGH)).record();

		InstantRecording recording = this.listener.getInstantRecording();
		assertThat(recording.getEvent()).isSameAs(INSTANT_EVENT);
		assertThat(recording.getHighCardinalityName()).isEqualTo("test-instant-event-12345");
		assertThat(recording.getTags()).containsExactly(Tag.of("testKey1", "testValue1", LOW),
				Tag.of("testKey2", "testValue2", HIGH));
		assertThat(recording.getWallTime()).isEqualTo(this.clock.wallTime());
		assertThat(recording.toString()).contains("event=test-instant-event")
				.contains("highCardinalityName=test-instant-event-12345")
				.contains("tags=[tag{testKey1=testValue1}, tag{testKey2=testValue2}]");
	}

	@Test
	void shouldRecordInstantEventWithProvidedTime() {
		Recorder<CompositeContext> recorder = new SimpleRecorder<>(new AllMatchingCompositeRecordingListener(this.listener),
				null);
		recorder.recordingFor(INSTANT_EVENT).highCardinalityName(INSTANT_EVENT.getLowCardinalityName() + "-12345")
				.tag(Tag.of("testKey1", "testValue1", LOW)).tag(Tag.of("testKey2", "testValue2", HIGH)).record(100);

		InstantRecording recording = this.listener.getInstantRecording();
		assertThat(recording.getEvent()).isSameAs(INSTANT_EVENT);
		assertThat(recording.getHighCardinalityName()).isEqualTo("test-instant-event-12345");
		assertThat(recording.getTags()).containsExactly(Tag.of("testKey1", "testValue1", LOW),
				Tag.of("testKey2", "testValue2", HIGH));
		assertThat(recording.getWallTime()).isEqualTo(100);
		assertThat(recording.toString()).contains("event=test-instant-event")
				.contains("highCardinalityName=test-instant-event-12345")
				.contains("tags=[tag{testKey1=testValue1}, tag{testKey2=testValue2}]");
	}

	@Test
	void shouldNotRecordInstantEventIfRecordingIsDisabled() {
		this.recorder.setEnabled(false);
		InstantRecording recording = this.recorder.recordingFor(INSTANT_EVENT)
				.highCardinalityName(INSTANT_EVENT.getLowCardinalityName() + "-12345")
				.tag(Tag.of("testKey1", "testValue1", LOW));
		recording.record();

		assertThat(this.recorder.isEnabled()).isFalse();
		assertThat(recording).isExactlyInstanceOf(NoOpInstantRecording.class);
		assertThat(this.listener.getInstantRecording()).isNull();

		assertThat(recording.getEvent().getLowCardinalityName()).isEqualTo("noop");
		assertThat(recording.getEvent().getDescription()).isEqualTo("noop");
		assertThat(recording.getHighCardinalityName()).isEqualTo("noop");
		assertThat(recording.getTags()).isEmpty();
		assertThat(recording.getWallTime()).isEqualTo(0);
		assertThat(recording).hasToString("NoOpInstantRecording");
	}

	@Test
	void shouldNotRecordInstantEventWithProvidedTimeIfRecordingIsDisabled() {
		Recorder<CompositeContext> recorder = new SimpleRecorder<>(new AllMatchingCompositeRecordingListener(this.listener),
				null);
		recorder.setEnabled(false);
		InstantRecording recording = recorder.recordingFor(INSTANT_EVENT)
				.highCardinalityName(INSTANT_EVENT.getLowCardinalityName() + "-12345")
				.tag(Tag.of("testKey1", "testValue1", LOW));
		recording.record(100);

		assertThat(recorder.isEnabled()).isFalse();
		assertThat(recording).isExactlyInstanceOf(NoOpInstantRecording.class);
		assertThat(this.listener.getInstantRecording()).isNull();

		assertThat(recording.getEvent().getLowCardinalityName()).isEqualTo("noop");
		assertThat(recording.getEvent().getDescription()).isEqualTo("noop");
		assertThat(recording.getHighCardinalityName()).isEqualTo("noop");
		assertThat(recording.getTags()).isEmpty();
		assertThat(recording.getWallTime()).isEqualTo(0);
		assertThat(recording).hasToString("NoOpInstantRecording");
	}

	@Test
	void shouldRecordIntervalEvent() {
		IntervalRecording<CompositeContext> recording = this.recorder.recordingFor(INTERVAL_EVENT)
				.tag(Tag.of("testKey1", "testValue1", LOW)).tag(Tag.of("testKey2", "testValue2", LOW)).start();

		verifyOnStart();

		try {
			this.clock.addSeconds(5);
			recording.tag(Tag.of("testKey3", "testValue3", HIGH));
			recording.error(new IOException("simulated"));

			verifyOnError();
		}
		finally {
			recording.highCardinalityName(INTERVAL_EVENT.getLowCardinalityName() + "-12345").stop();
			verifyOnStop();
		}
	}

	@Test
	void shouldRecordIntervalEventWithProvidedTime() {
		Recorder<CompositeContext> recorder = new SimpleRecorder<>(new AllMatchingCompositeRecordingListener(this.listener),
				null);
		IntervalRecording<CompositeContext> recording = recorder.recordingFor(INTERVAL_EVENT)
				.tag(Tag.of("testKey1", "testValue1", LOW)).tag(Tag.of("testKey2", "testValue2", LOW)).start(1, 2);

		verifyOnStart(1, 2);

		try {
			recording.tag(Tag.of("testKey3", "testValue3", HIGH));
			recording.error(new IOException("simulated"));

			verifyOnError(1, 2);
		}
		finally {
			recording.highCardinalityName(INTERVAL_EVENT.getLowCardinalityName() + "-12345")
					.stop(TimeUnit.MILLISECONDS.toNanos(100) + 2);
			verifyOnStop(1, 2, TimeUnit.MILLISECONDS.toNanos(100) + 2);
		}
	}

	@Test
	void shouldNotRecordIntervalEventIfRecordingIsDisabled() {
		this.recorder.setEnabled(false);
		IntervalRecording<CompositeContext> recording = this.recorder.recordingFor(INTERVAL_EVENT)
				.tag(Tag.of("testKey1", "testValue1", LOW)).start();

		try {
			this.clock.addSeconds(5);
			recording.error(new IOException("simulated"));
		}
		finally {
			recording.highCardinalityName(INTERVAL_EVENT.getLowCardinalityName()).stop();
		}

		assertThat(this.recorder.isEnabled()).isFalse();
		assertThat(recording).isExactlyInstanceOf(NoOpIntervalRecording.class);
		assertThat(this.listener.getOnStartRecording()).isNull();
		assertThat(this.listener.getOnStopRecording()).isNull();
		assertThat(this.listener.getOnErrorRecording()).isNull();

		assertThat(recording.getEvent().getLowCardinalityName()).isSameAs("noop");
		assertThat(recording.getEvent().getDescription()).isSameAs("noop");
		assertThat(recording.getHighCardinalityName()).isSameAs("noop");
		assertThat(recording.getDuration()).isSameAs(Duration.ZERO);
		assertThat(recording.getStartNanos()).isEqualTo(0);
		assertThat(recording.getStopNanos()).isEqualTo(0);
		assertThat(recording.getStartWallTime()).isEqualTo(0);

		assertThat(recording.getError()).isNull();
		assertThat(recording.getTags()).isEmpty();
		assertThat(recording.getContext()).isNull();
		assertThat(recording).hasToString("NoOpIntervalRecording");
	}

	@Test
	void shouldNotRecordIntervalEventWithProvidedTimeIfRecordingIsDisabled() {
		this.recorder.setEnabled(false);
		IntervalRecording<CompositeContext> recording = this.recorder.recordingFor(INTERVAL_EVENT)
				.tag(Tag.of("testKey1", "testValue1", LOW)).start(1, 2);

		try {
			recording.error(new IOException("simulated"));
		}
		finally {
			recording.highCardinalityName(INTERVAL_EVENT.getLowCardinalityName()).stop();
		}

		assertThat(this.recorder.isEnabled()).isFalse();
		assertThat(recording).isExactlyInstanceOf(NoOpIntervalRecording.class);
		assertThat(this.listener.getOnStartRecording()).isNull();
		assertThat(this.listener.getOnStopRecording()).isNull();
		assertThat(this.listener.getOnErrorRecording()).isNull();

		assertThat(recording.getEvent().getLowCardinalityName()).isSameAs("noop");
		assertThat(recording.getEvent().getDescription()).isSameAs("noop");
		assertThat(recording.getHighCardinalityName()).isSameAs("noop");
		assertThat(recording.getDuration()).isSameAs(Duration.ZERO);
		assertThat(recording.getStartNanos()).isEqualTo(0);
		assertThat(recording.getStopNanos()).isEqualTo(0);
		assertThat(recording.getStartWallTime()).isEqualTo(0);

		assertThat(recording.getError()).isNull();
		assertThat(recording.getTags()).isEmpty();
		assertThat(recording.getContext()).isNull();
		assertThat(recording).hasToString("NoOpIntervalRecording");
	}

	// Used in presentation slides
	@Test
	void shouldShowSimpleUsageScenario() {
		String userId = "1";
		CalculationService calculationService = new CalculationService();
		SimpleRecorder<?> recorder = new SimpleRecorder<>(this.listener, Clock.SYSTEM);

		IntervalRecording recording = recorder.recordingFor((IntervalEvent) () -> "important-calculation")
				.tag(Tag.of("calculation-type", "tax", Cardinality.LOW))
				.tag(Tag.of("user-id", userId, Cardinality.HIGH))
				.start();
		try {
			calculationService.calculate();
		}
		catch (Exception exception) {
			recording.error(exception);
			throw exception;
		}
		finally {
			recording.stop();
		}
	}

	private void verifyOnStart() {
		verifyOnStart(this.listener.getOnStartSnapshot().wallTime(), this.listener.getOnStartSnapshot().monotonicTime());
	}

	private void verifyOnStart(long startWallTime, long startNanos) {
		IntervalRecording<TestContext> recording = this.listener.getOnStartRecording();

		assertThat(this.listener.getOnErrorRecording()).isNull();
		assertThat(this.listener.getOnStopRecording()).isNull();

		assertThat(recording.getEvent()).isSameAs(INTERVAL_EVENT);
		assertThat(recording.getHighCardinalityName()).isEqualTo("test-interval-event");
		assertThat(recording.getDuration()).isSameAs(Duration.ZERO);
		assertThat(recording.getStartNanos()).isEqualTo(startNanos);
		assertThat(recording.getStopNanos()).isEqualTo(0);
		assertThat(recording.getStartWallTime()).isEqualTo(startWallTime);

		assertThat(recording.getError()).isNull();
		assertThat(recording.getTags()).containsExactly(Tag.of("testKey1", "testValue1", LOW),
				Tag.of("testKey2", "testValue2", LOW));
		assertThat(recording.getContext()).isSameAs(this.listener.getContext());
		assertThat(recording.toString()).contains("event=test-interval-event")
				.contains("highCardinalityName=test-interval-event")
				.contains("tags=[tag{testKey1=testValue1}, tag{testKey2=testValue2}]").contains("duration=0ms")
				.contains("error=null");
	}

	private void verifyOnError() {
		verifyOnError(this.listener.getOnStartSnapshot().wallTime(), this.listener.getOnStartSnapshot().monotonicTime());
	}

	private void verifyOnError(long startWallTime, long startNanos) {
		IntervalRecording<TestContext> recording = this.listener.getOnErrorRecording();

		assertThat(this.listener.getOnStartRecording()).isNotNull();
		assertThat(this.listener.getOnStopRecording()).isNull();

		assertThat(recording.getEvent()).isSameAs(INTERVAL_EVENT);
		assertThat(recording.getHighCardinalityName()).isEqualTo("test-interval-event");
		assertThat(recording.getDuration()).isSameAs(Duration.ZERO);
		assertThat(recording.getStartNanos()).isEqualTo(startNanos);
		assertThat(recording.getStopNanos()).isEqualTo(0);
		assertThat(recording.getStartWallTime()).isEqualTo(startWallTime);

		assertThat(recording.getError()).isExactlyInstanceOf(IOException.class).hasMessage("simulated").hasNoCause();
		assertThat(recording.getTags()).containsExactly(Tag.of("testKey1", "testValue1", LOW),
				Tag.of("testKey2", "testValue2", LOW), Tag.of("testKey3", "testValue3", HIGH));
		assertThat(recording.getContext()).isSameAs(this.listener.getContext());
		assertThat(recording.toString()).contains("event=test-interval-event")
				.contains("highCardinalityName=test-interval-event")
				.contains("tags=[tag{testKey1=testValue1}, tag{testKey2=testValue2}, tag{testKey3=testValue3}]")
				.contains("duration=0ms").contains("error=java.io.IOException: simulated");
	}

	private void verifyOnStop() {
		verifyOnStop(this.listener.getOnStartSnapshot().wallTime(), this.listener.getOnStartSnapshot().monotonicTime(),
				this.listener.getOnStopSnapshot().monotonicTime());
	}

	private void verifyOnStop(long startWallTime, long startNanos, long stopNanos) {
		IntervalRecording<TestContext> recording = this.listener.getOnStopRecording();
		Duration duration = Duration.ofNanos(stopNanos - startNanos);

		assertThat(this.listener.getOnStartRecording()).isNotNull();
		assertThat(this.listener.getOnErrorRecording()).isNotNull();

		assertThat(recording.getEvent()).isSameAs(INTERVAL_EVENT);
		assertThat(recording.getHighCardinalityName()).isEqualTo("test-interval-event-12345");
		assertThat(recording.getDuration()).isEqualTo(duration);
		assertThat(recording.getStartNanos()).isEqualTo(startNanos);
		assertThat(recording.getStopNanos()).isEqualTo(stopNanos);
		assertThat(recording.getStartWallTime()).isEqualTo(startWallTime);

		assertThat(recording.getError()).isExactlyInstanceOf(IOException.class).hasMessage("simulated").hasNoCause();
		assertThat(recording.getTags()).containsExactly(Tag.of("testKey1", "testValue1", LOW),
				Tag.of("testKey2", "testValue2", LOW), Tag.of("testKey3", "testValue3", HIGH));
		assertThat(recording.getContext()).isSameAs(this.listener.getContext());
		assertThat(recording.toString()).contains("event=test-interval-event")
				.contains("highCardinalityName=test-interval-event-12345")
				.contains("tags=[tag{testKey1=testValue1}, tag{testKey2=testValue2}, tag{testKey3=testValue3}]")
				.contains("duration=" + duration.toMillis() + "ms").contains("error=java.io.IOException: simulated");
	}

	class CalculationService {

		void calculate() {

		}

		;

	}

}
