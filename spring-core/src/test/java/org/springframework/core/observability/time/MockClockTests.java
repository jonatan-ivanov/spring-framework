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

package org.springframework.core.observability.time;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonatan Ivanov
 */
class MockClockTests {

	private final MockClock clock = new MockClock();

	@Test
	void initialTimeShouldNotBeZero() {
		assertThat(this.clock.monotonicTime()).isNotZero();
		assertThat(this.clock.wallTime()).isNotZero();
	}

	@Test
	void timeShouldBeIncrementedByAdd() {
		long initMonotonicTime = this.clock.monotonicTime();
		long initWallTime = this.clock.wallTime();

		this.clock.add(100, NANOSECONDS);

		assertThat(this.clock.monotonicTime()).isEqualTo(initMonotonicTime + 100);
		assertThat(this.clock.wallTime()).isEqualTo(initWallTime + 100);

		this.clock.add(Duration.ofMillis(100));

		assertThat(this.clock.monotonicTime()).isEqualTo(initMonotonicTime + 100 + MILLISECONDS.toNanos(100));
		assertThat(this.clock.wallTime()).isEqualTo(initWallTime + 100 + MILLISECONDS.toNanos(100));

		this.clock.addSeconds(1);

		assertThat(this.clock.monotonicTime())
				.isEqualTo(initMonotonicTime + 100 + MILLISECONDS.toNanos(100) + SECONDS.toNanos(1));
		assertThat(this.clock.wallTime()).isEqualTo(initWallTime + 100 + MILLISECONDS.toNanos(100) + SECONDS.toNanos(1));
	}

	@Test
	void shouldReturnTimeWithCorrectUnit() {
		this.clock.add(1, SECONDS);
		assertThat(this.clock.monotonicTimeIn(MICROSECONDS)).isEqualTo(NANOSECONDS.toMicros(this.clock.monotonicTime()));
		assertThat(this.clock.wallTimeIn(MICROSECONDS)).isEqualTo(NANOSECONDS.toMicros(this.clock.wallTime()));
	}

}
