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

import org.junit.jupiter.api.Test;

import org.springframework.core.observability.event.Recording;
import org.springframework.core.observability.event.instant.InstantRecording;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.event.listener.RecordingListener;

import static org.assertj.core.api.Assertions.assertThat;

class FirstMatchingCompositeRecordingListenerTests {

	MatchingListener matchingListener = new MatchingListener();

	@Test
	void should_run_on_start_only_for_first_matching_listener() {
		FirstMatchingCompositeRecordingListener firstMatchingCompositeRecordingListener = new FirstMatchingCompositeRecordingListener(
				new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

		firstMatchingCompositeRecordingListener.onStart(null);

		assertThat(this.matchingListener.started).isTrue();
	}

	@Test
	void should_run_on_stop_only_for_first_matching_listener() {
		FirstMatchingCompositeRecordingListener firstMatchingCompositeRecordingListener = new FirstMatchingCompositeRecordingListener(
				new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

		firstMatchingCompositeRecordingListener.onStop(null);

		assertThat(this.matchingListener.stopped).isTrue();
	}

	@Test
	void should_run_on_error_only_for_first_matching_listener() {
		FirstMatchingCompositeRecordingListener firstMatchingCompositeRecordingListener = new FirstMatchingCompositeRecordingListener(
				new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

		firstMatchingCompositeRecordingListener.onError(null);

		assertThat(this.matchingListener.errored).isTrue();
	}

	@Test
	void should_run_on_restore_only_for_first_matching_listener() {
		FirstMatchingCompositeRecordingListener firstMatchingCompositeRecordingListener = new FirstMatchingCompositeRecordingListener(
				new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

		firstMatchingCompositeRecordingListener.onRestore(null);

		assertThat(this.matchingListener.restored).isTrue();
	}

	@Test
	void should_run_on_create_only_for_first_matching_listener() {
		FirstMatchingCompositeRecordingListener firstMatchingCompositeRecordingListener = new FirstMatchingCompositeRecordingListener(
				new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

		firstMatchingCompositeRecordingListener.onCreate(null);

		assertThat(this.matchingListener.created).isTrue();
	}

	@Test
	void should_run_record_only_for_first_matching_listener() {
		FirstMatchingCompositeRecordingListener firstMatchingCompositeRecordingListener = new FirstMatchingCompositeRecordingListener(
				new NotMatchingListener(), this.matchingListener, new NotMatchingListener());

		firstMatchingCompositeRecordingListener.record(null);

		assertThat(this.matchingListener.recorded).isTrue();
	}

	static class MatchingListener implements RecordingListener {

		boolean created;

		boolean started;

		boolean stopped;

		boolean errored;

		boolean recorded;

		boolean restored;

		@Override
		public Object createContext() {
			return null;
		}

		@Override
		public void onCreate(IntervalRecording intervalRecording) {
			this.created = true;
		}

		@Override
		public void onStart(IntervalRecording intervalRecording) {
			this.started = true;
		}

		@Override
		public void onStop(IntervalRecording intervalRecording) {
			this.stopped = true;
		}

		@Override
		public void onError(IntervalRecording intervalRecording) {
			this.errored = true;
		}

		@Override
		public void onRestore(IntervalRecording intervalRecording) {
			this.restored = true;
		}

		@Override
		public void record(InstantRecording instantRecording) {
			this.recorded = true;
		}

	}

	static class NotMatchingListener implements RecordingListener {

		@Override
		public Object createContext() {
			return null;
		}

		@Override
		public void onCreate(IntervalRecording intervalRecording) {
			throwAssertionError();
		}

		@Override
		public void onStart(IntervalRecording intervalRecording) {
			throwAssertionError();
		}

		private void throwAssertionError() {
			throw new AssertionError("Not matching listener must not be called");
		}

		@Override
		public void onStop(IntervalRecording intervalRecording) {
			throwAssertionError();
		}

		@Override
		public void onError(IntervalRecording intervalRecording) {
			throwAssertionError();
		}

		@Override
		public void onRestore(IntervalRecording intervalRecording) {
			throwAssertionError();
		}

		@Override
		public void record(InstantRecording instantRecording) {
			throwAssertionError();
		}

		@Override
		public boolean isApplicable(Recording recording) {
			return false;
		}

	}

}
