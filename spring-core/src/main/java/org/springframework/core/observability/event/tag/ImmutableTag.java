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

package org.springframework.core.observability.event.tag;

import java.util.Objects;

import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

/**
 * Simple, immutable implementation of the {@link Tag} interface.
 *
 * @author Jonatan Ivanov
 * @since 6.0.0
 */
public class ImmutableTag implements Tag {

	private final String key;

	private final String value;

	private final Cardinality cardinality;

	/**
	 * Creates a new instance of {@link ImmutableTag}.
	 *
	 * @param key the key of the tag, it mustn't be null.
	 * @param value the value of the tag, it mustn't be null.
	 * @param cardinality the cardinality of the tag, it mustn't be null.
	 */
	public ImmutableTag(@NonNull String key, @NonNull String value, @NonNull Cardinality cardinality) {
		Assert.notNull(key, "key can't be null");
		Assert.notNull(value, "value can't be null");
		Assert.notNull(cardinality, "cardinality can't be null");
		this.key = key;
		this.value = value;
		this.cardinality = cardinality;
	}

	@Override
	@NonNull
	public String getKey() {
		return this.key;
	}

	@Override
	@NonNull
	public String getValue() {
		return this.value;
	}

	@Override
	@NonNull
	public Cardinality getCardinality() {
		return this.cardinality;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ImmutableTag that = (ImmutableTag) o;
		return this.key.equals(that.key) && this.value.equals(that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.key, this.value);
	}

	@Override
	public String toString() {
		return "tag{" + this.key + "=" + this.value + "}";
	}

}
