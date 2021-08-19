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

package org.springframework.core.observability.event.listener.composite;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.core.observability.event.instant.InstantRecording;
import org.springframework.core.observability.event.interval.IntervalEvent;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.event.tag.Tag;
import org.springframework.core.observability.test.TestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.reset;
import static org.mockito.BDDMockito.then;
import static org.springframework.core.observability.event.tag.Cardinality.HIGH;
import static org.springframework.core.observability.test.TestIntervalEvent.INTERVAL_EVENT;

/**
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class AllMatchingCompositeRecordingListenerTests {

	private final TestContext context1 = new TestContext();

	private final String context2 = "context2";

	private final Void context3 = null;

	@Mock
	private RecordingListener<TestContext> listener1;

	@Mock
	private RecordingListener<String> listener2;

	@Mock
	private RecordingListener<Void> listener3;

	@Captor
	private ArgumentCaptor<IntervalRecordingView<TestContext>> captor1;

	@Captor
	private ArgumentCaptor<IntervalRecordingView<String>> captor2;

	@Captor
	private ArgumentCaptor<IntervalRecordingView<Void>> captor3;

	@Mock
	private InstantRecording instantRecording;

	@Mock
	private IntervalRecording<CompositeContext> intervalRecording;

	private RecordingListener<CompositeContext> compositeListener;

	@BeforeEach
	void setUp() {
		this.compositeListener = new AllMatchingCompositeRecordingListener(this.listener1, this.listener2, this.listener3);
	}

	private void setupListeners() {
		given(this.listener1.isApplicable(any())).willReturn(true);
		given(this.listener2.isApplicable(any())).willReturn(true);
		given(this.listener3.isApplicable(any())).willReturn(true);
	}

	@Test
	void recordShouldDelegate() {
		setupListeners();

		this.compositeListener.record(this.instantRecording);

		then(this.listener1).should().record(this.instantRecording);
		then(this.listener2).should().record(this.instantRecording);
		then(this.listener3).should().record(this.instantRecording);
	}

	@Test
	void onStartShouldDelegate() {
		setupListeners();
		given(this.listener1.createContext()).willReturn(this.context1);
		given(this.listener2.createContext()).willReturn(this.context2);
		given(this.listener3.createContext()).willReturn(this.context3);

		this.compositeListener.onStart(this.intervalRecording);

		then(this.listener1).should().onStart(this.captor1.capture());
		then(this.listener2).should().onStart(this.captor2.capture());
		then(this.listener3).should().onStart(this.captor3.capture());

		assertThatViewWrapsRecording(this.listener1, this.captor1.getValue(), this.intervalRecording);
		assertThatViewWrapsRecording(this.listener2, this.captor2.getValue(), this.intervalRecording);
		assertThatViewWrapsRecording(this.listener3, this.captor3.getValue(), this.intervalRecording);
	}

	@Test
	void onErrorShouldDelegate() {
		setupListeners();
		given(this.listener1.createContext()).willReturn(this.context1);
		given(this.listener2.createContext()).willReturn(this.context2);
		given(this.listener3.createContext()).willReturn(this.context3);

		this.compositeListener.onError(this.intervalRecording);

		then(this.listener1).should().onError(this.captor1.capture());
		then(this.listener2).should().onError(this.captor2.capture());
		then(this.listener3).should().onError(this.captor3.capture());

		assertThatViewWrapsRecording(this.listener1, this.captor1.getValue(), this.intervalRecording);
		assertThatViewWrapsRecording(this.listener2, this.captor2.getValue(), this.intervalRecording);
		assertThatViewWrapsRecording(this.listener3, this.captor3.getValue(), this.intervalRecording);
	}

	@Test
	void onStopShouldDelegate() {
		setupListeners();
		given(this.listener1.createContext()).willReturn(this.context1);
		given(this.listener2.createContext()).willReturn(this.context2);
		given(this.listener3.createContext()).willReturn(this.context3);

		this.compositeListener.onStop(this.intervalRecording);

		then(this.listener1).should().onStop(this.captor1.capture());
		then(this.listener2).should().onStop(this.captor2.capture());
		then(this.listener3).should().onStop(this.captor3.capture());

		assertThatViewWrapsRecording(this.listener1, this.captor1.getValue(), this.intervalRecording);
		assertThatViewWrapsRecording(this.listener2, this.captor2.getValue(), this.intervalRecording);
		assertThatViewWrapsRecording(this.listener3, this.captor3.getValue(), this.intervalRecording);
	}

	@Test
	void createContextShouldReturnComposite() {
		given(this.listener1.createContext()).willReturn(this.context1);
		given(this.listener2.createContext()).willReturn(this.context2);
		given(this.listener3.createContext()).willReturn(this.context3);

		CompositeContext context = this.compositeListener.createContext();

		assertThat(context.byListener(this.listener1)).isSameAs(this.context1);
		assertThat(context.byListener(this.listener2)).isSameAs(this.context2);
		assertThat(context.byListener(this.listener3)).isSameAs(this.context3);
	}

	private <T> void assertThatViewWrapsRecording(RecordingListener<T> listener, IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		assertThatGetEventDelegates(recordingView, recording);
		assertThatGetHighCardinalityNameDelegates(recordingView, recording);
		assertThatHighCardinalityNameDelegates(recordingView, recording);
		assertThatGetTagsDelegates(recordingView, recording);
		assertThatTagsDelegates(recordingView, recording);
		assertThatGetDurationDelegates(recordingView, recording);
		assertThatGetStartNanosDelegates(recordingView, recording);
		assertThatGetStopNanosDelegates(recordingView, recording);
		assertThatGetStartWallTimeDelegates(recordingView, recording);
		assertThatStartDelegates(recordingView, recording);
		assertThatStartWithProvidedTimeDelegates(recordingView, recording);
		assertThatStopDelegates(recordingView, recording);
		assertThatStopWithProvidedTimeDelegates(recordingView, recording);
		assertThatGetErrorDelegates(recordingView, recording);
		assertThatErrorDelegates(recordingView, recording);
		assertThatGetContextDelegates(listener, recordingView, recording);
		assertThatToStringDelegates(recordingView, recording);

		reset(recording);
	}

	private <T> void assertThatGetEventDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		given(recording.getEvent()).willReturn(INTERVAL_EVENT);
		IntervalEvent actualEvent = recordingView.getEvent();

		then(recording).should().getEvent();
		assertThat(actualEvent).isSameAs(recording.getEvent());
	}

	private <T> void assertThatGetHighCardinalityNameDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		given(recording.getHighCardinalityName()).willReturn("12345");
		String actualHighCardinalityName = recordingView.getHighCardinalityName();

		then(recording).should().getHighCardinalityName();
		assertThat(actualHighCardinalityName).isEqualTo("12345");
	}

	private <T> void assertThatHighCardinalityNameDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		String highCardinalityName = "12345";
		given(recording.highCardinalityName(highCardinalityName)).willReturn(recording);

		IntervalRecording<T> actualRecording = recordingView.highCardinalityName(highCardinalityName);
		then(recording).should().highCardinalityName(highCardinalityName);
		assertThat(actualRecording).isSameAs(recording);
	}

	private <T> void assertThatGetTagsDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		Tag tag = Tag.of("a", "b", HIGH);
		given(recording.getTags()).willReturn(Collections.singletonList(tag));

		Iterable<Tag> actualTags = recordingView.getTags();
		then(recording).should().getTags();
		assertThat(actualTags).containsExactly(tag);
	}

	private <T> void assertThatTagsDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		Tag tag = Tag.of("a", "b", HIGH);
		given(recording.tag(tag)).willReturn(recording);

		IntervalRecording<T> actualRecording = recordingView.tag(tag);
		then(recording).should().tag(tag);
		assertThat(actualRecording).isSameAs(recording);
	}

	private <T> void assertThatGetDurationDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		Duration duration = Duration.ofMillis(42);
		given(recording.getDuration()).willReturn(duration);

		Duration actualDuration = recordingView.getDuration();
		then(recording).should().getDuration();
		assertThat(actualDuration).isSameAs(duration);
	}

	private <T> void assertThatGetStartNanosDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		long startNanos = 12;
		given(recording.getStartNanos()).willReturn(startNanos);

		long actualStartNanos = recordingView.getStartNanos();
		then(recording).should().getStartNanos();
		assertThat(actualStartNanos).isEqualTo(startNanos);
	}

	private <T> void assertThatGetStopNanosDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		long stopNanos = 42;
		given(recording.getStopNanos()).willReturn(stopNanos);

		long actualStopNanos = recordingView.getStopNanos();
		then(recording).should().getStopNanos();
		assertThat(actualStopNanos).isEqualTo(stopNanos);
	}

	private <T> void assertThatGetStartWallTimeDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		long startWallTime = 12;
		given(recording.getStartWallTime()).willReturn(startWallTime);

		long actualStartWallTime = recordingView.getStartWallTime();
		then(recording).should().getStartWallTime();
		assertThat(actualStartWallTime).isEqualTo(startWallTime);
	}

	private <T> void assertThatStartDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		given(recording.start()).willReturn(recording);

		IntervalRecording<T> actualRecording = recordingView.start();
		then(recording).should().start();
		assertThat(actualRecording).isSameAs(recording);
	}

	private <T> void assertThatStartWithProvidedTimeDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		given(recording.start(1, 1)).willReturn(recording);

		IntervalRecording<T> actualRecording = recordingView.start(1, 1);
		then(recording).should().start(1, 1);
		assertThat(actualRecording).isSameAs(recording);
	}

	private <T> void assertThatStopDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		recordingView.stop();
		then(recording).should().stop();
	}

	private <T> void assertThatStopWithProvidedTimeDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		recordingView.stop(1);
		then(recording).should().stop(1);
	}

	private <T> void assertThatGetErrorDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		Throwable error = new IOException("simulated");
		given(recording.getError()).willReturn(error);

		Throwable actualError = recordingView.getError();
		then(recording).should().getError();
		assertThat(actualError).isSameAs(error);
	}

	private <T> void assertThatErrorDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		Throwable error = new IOException("simulated");
		given(recording.error(error)).willReturn(recording);

		IntervalRecording<T> actualRecording = recordingView.error(error);
		then(recording).should().error(error);
		assertThat(actualRecording).isSameAs(recording);
	}

	private <T> void assertThatGetContextDelegates(RecordingListener<T> listener,
			IntervalRecordingView<T> recordingView, IntervalRecording<CompositeContext> recording) {
		T context = listener.createContext();
		CompositeContext compositeContext = mock(CompositeContext.class);
		given(recording.getContext()).willReturn(compositeContext);
		given(compositeContext.byListener(listener)).willReturn(context);

		T actualContext = recordingView.getContext();
		then(recording).should().getContext();
		assertThat(actualContext).isSameAs(context);
	}

	private <T> void assertThatToStringDelegates(IntervalRecordingView<T> recordingView,
			IntervalRecording<CompositeContext> recording) {
		String toString = "{test}";
		given(recording.toString()).willReturn(toString);

		String actualToString = recordingView.toString();
		// then(recording).toString(); //Mockito cannot verify toString() :(
		assertThat(actualToString).isSameAs(toString);
	}

}
