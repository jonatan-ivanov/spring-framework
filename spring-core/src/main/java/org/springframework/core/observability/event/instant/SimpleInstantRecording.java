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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.observability.event.listener.RecordingListener;
import org.springframework.core.observability.event.tag.Tag;
import org.springframework.core.observability.time.Clock;

/**
 * Simple implementation of a {@link InstantRecording}.
 *
 * @author Jonatan Ivanov
 * @since 6.0.0
 */
public class SimpleInstantRecording implements InstantRecording {

	private final InstantEvent event;

	private final RecordingListener<?> listener;

	private final Clock clock;

	private final Set<Tag> tags = new LinkedHashSet<>();

	private String highCardinalityName;

	private long wallTime = 0;

	/**
	 * Creates a new {@link SimpleInstantRecording}.
	 *
	 * @param event the event this recording belongs to
	 * @param listener the listener that needs to be notified about the recordings
	 * @param clock the clock to be used
	 */
	public SimpleInstantRecording(InstantEvent event, RecordingListener<?> listener, Clock clock) {
		this.event = event;
		this.highCardinalityName = event.getLowCardinalityName();
		this.listener = listener;
		this.clock = clock;
	}

	@Override
	public InstantEvent getEvent() {
		return this.event;
	}

	@Override
	public String getHighCardinalityName() {
		return this.highCardinalityName;
	}

	@Override
	public InstantRecording highCardinalityName(String highCardinalityName) {
		this.highCardinalityName = highCardinalityName;
		return this;
	}

	@Override
	public Iterable<Tag> getTags() {
		return Collections.unmodifiableSet(this.tags);
	}

	@Override
	public InstantRecording tag(Tag tag) {
		this.tags.add(tag);
		return this;
	}

	@Override
	public void record() {
		record(this.clock.wallTime());
	}

	@Override
	public void record(long wallTime) {
		this.wallTime = wallTime;
		this.listener.record(this);
	}

	@Override
	public long getWallTime() {
		return this.wallTime;
	}

	@Override
	public String toString() {
		return "{" + "event=" + this.event.getLowCardinalityName() + ", highCardinalityName=" + this.highCardinalityName
				+ ", tags=" + this.tags + '}';
	}

}
