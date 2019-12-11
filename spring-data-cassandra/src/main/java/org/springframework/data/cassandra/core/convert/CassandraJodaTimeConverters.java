/*
 * Copyright 2016-2020 the original author or authors.
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
package org.springframework.data.cassandra.core.convert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.joda.time.LocalTime;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.cassandra.core.mapping.CassandraSimpleTypeHolder;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.util.ClassUtils;

/**
 * Helper class to register JSR-310 specific {@link Converter} implementations to convert between Cassandra types in
 * case the library is present on the classpath.
 *
 * @author Mark Paluch
 * @since 1.5
 */
@SuppressWarnings("deprecation")
public abstract class CassandraJodaTimeConverters {

	private static final boolean JODA_TIME_IS_PRESENT = ClassUtils.isPresent("org.joda.time.LocalDate", null);

	private CassandraJodaTimeConverters() {}

	/**
	 * Returns the converters to be registered. Will only return converters in case JodaTime is present on the class path.
	 *
	 * @return a {@link Collection} of Joda Time {@link Converter Converters} to register.
	 * @see org.springframework.core.convert.converter.Converter
	 * @see java.util.Collection
	 * @see org.joda.time
	 */
	public static Collection<Converter<?, ?>> getConvertersToRegister() {

		if (!JODA_TIME_IS_PRESENT) {
			return Collections.emptySet();
		}

		List<Converter<?, ?>> converters = new ArrayList<>();

		converters.add(MillisOfDayToLocalTimeConverter.INSTANCE);
		converters.add(LocalTimeToMillisOfDayConverter.INSTANCE);

		return converters;
	}

	/**
	 * Simple singleton to convert {@link Long}s to their {@link LocalTime} representation.
	 *
	 * @author Mark Paluch
	 */
	@ReadingConverter
	public enum MillisOfDayToLocalTimeConverter implements Converter<Long, LocalTime> {

		INSTANCE;

		@Override
		public LocalTime convert(Long source) {
			return LocalTime.fromMillisOfDay(source);
		}
	}

	/**
	 * Simple singleton to convert {@link LocalTime}s to their {@link Long} representation.
	 *
	 * @author Mark Paluch
	 */
	@WritingConverter
	@CassandraType(type = CassandraSimpleTypeHolder.Name.TIME)
	public enum LocalTimeToMillisOfDayConverter implements Converter<LocalTime, Long> {

		INSTANCE;

		@Override
		public Long convert(LocalTime source) {
			return (long) source.getMillisOfDay();
		}
	}
}
