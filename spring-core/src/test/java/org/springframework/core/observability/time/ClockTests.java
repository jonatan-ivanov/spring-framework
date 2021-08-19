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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonatan Ivanov
 */
class ClockTests {

	private final Clock clock = Clock.SYSTEM;

	private final long initMonotonicTime = this.clock.monotonicTime();

	private final long initWallTime = this.clock.wallTime();

	@Test
	void nowShouldBeGreaterThanInit() throws InterruptedException {
		Thread.sleep(1L);
		assertThat(this.clock.monotonicTime()).isGreaterThan(this.initMonotonicTime);
		assertThat(this.clock.wallTime()).isGreaterThan(this.initWallTime);
	}

}
