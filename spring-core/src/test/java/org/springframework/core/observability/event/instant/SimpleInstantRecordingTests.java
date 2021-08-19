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

package org.springframework.core.observability.event.instant;

import org.junit.jupiter.api.Test;

import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.event.tag.Tag;
import org.springframework.core.observability.time.MockClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.core.observability.event.tag.Cardinality.HIGH;
import static org.springframework.core.observability.event.tag.Cardinality.LOW;
import static org.springframework.core.observability.test.TestInstantEvent.INSTANT_EVENT;

/**
 * @author Jonatan Ivanov
 */
class SimpleInstantRecordingTests {

	private final MockClock clock = new MockClock();

	private final RecordingListener<Void> listener = mock(RecordingListener.class);

	@Test
	void shouldReturnThePassedEvent() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock);
		assertThat(recording.getEvent()).isSameAs(INSTANT_EVENT);
	}

	@Test
	void shouldReturnTheRecordingWithHighCardinalityName() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock);

		assertThat(recording.getHighCardinalityName()).isSameAs(INSTANT_EVENT.getLowCardinalityName());
		String highCardinalityName = INSTANT_EVENT.getLowCardinalityName() + "-123456";
		recording.highCardinalityName(highCardinalityName);
		assertThat(recording.getHighCardinalityName()).isSameAs(highCardinalityName);
	}

	@Test
	void shouldHaveTagsWhenAdded() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock);

		assertThat(recording.getTags()).isEmpty();

		Tag tag1 = Tag.of("testKey1", "testValue1", LOW);
		Tag tag2 = Tag.of("testKey2", "testValue2", HIGH);
		Tag tag3 = Tag.of("testKey3", "testValue3", LOW);

		recording.tag(tag1);
		assertThat(recording.getTags()).containsExactly(tag1);

		recording.tag(tag2);
		assertThat(recording.getTags()).containsExactly(tag1, tag2);

		recording.tag(tag3);
		assertThat(recording.getTags()).containsExactly(tag1, tag2, tag3);
	}

	@Test
	void wallTimeShouldBeRecorded() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock);
		recording.record();
		assertThat(recording.getWallTime()).isEqualTo(this.clock.wallTime());
	}

	@Test
	void providedWallTimeShouldBeRecorded() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock);
		recording.record(42);
		assertThat(recording.getWallTime()).isEqualTo(42);
	}

	@Test
	void listenerShouldBeNotified() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock);
		recording.record();
		verify(this.listener).record(recording);
	}

	@Test
	void listenerShouldBeNotifiedAndEventTimeShouldBeStored() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, null);
		recording.record(1000L);

		verify(this.listener).record(recording);
		assertThat(recording.getWallTime()).isEqualTo(1000L);
	}

	@Test
	void toStringShouldWork() {
		InstantRecording recording = new SimpleInstantRecording(INSTANT_EVENT, this.listener, this.clock)
				.highCardinalityName(INSTANT_EVENT.getLowCardinalityName() + "-123")
				.tag(Tag.of("testKey1", "testValue1", LOW)).tag(Tag.of("testKey2", "testValue2", HIGH))
				.tag(Tag.of("testKey3", "testValue3", LOW));

		assertThat(recording.toString()).contains("event=test-instant-event")
				.contains("highCardinalityName=test-instant-event-123")
				.contains("tags=[tag{testKey1=testValue1}, tag{testKey2=testValue2}, tag{testKey3=testValue3}]");
	}

}
