/*
 * Copyright 2018-2020 the original author or authors.
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.data.cassandra.test.util.RowMockUtil.*;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;
import org.springframework.data.cassandra.core.mapping.UserTypeResolver;
import org.springframework.data.cassandra.support.UserDefinedTypeBuilder;
import org.springframework.data.cassandra.test.util.RowMockUtil;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataTypes;

/**
 * Unit tests for UDT through {@link MappingCassandraConverter}.
 *
 * @author Mark Paluch
 */
@RunWith(MockitoJUnitRunner.Silent.class) // there are some unused stubbings in RowMockUtil but they're used in other
public class MappingCassandraConverterUDTUnitTests {

	@Mock UserTypeResolver userTypeResolver;

	com.datastax.oss.driver.api.core.type.UserDefinedType manufacturer = UserDefinedTypeBuilder.forName("manufacturer")
			.withField("name", DataTypes.TEXT).withField("displayname", DataTypes.TEXT).build();
	com.datastax.oss.driver.api.core.type.UserDefinedType currency = UserDefinedTypeBuilder.forName("mycurrency")
			.withField("currency", DataTypes.TEXT).build();

	Row rowMock;

	CassandraMappingContext mappingContext;
	MappingCassandraConverter mappingCassandraConverter;

	@Before
	public void setUp() {

		mappingContext = new CassandraMappingContext();
		mappingContext.setUserTypeResolver(userTypeResolver);

		mappingCassandraConverter = new MappingCassandraConverter(mappingContext);
		mappingCassandraConverter.afterPropertiesSet();

		when(userTypeResolver.resolveType(CqlIdentifier.fromCql("manufacturer"))).thenReturn(manufacturer);
		when(userTypeResolver.resolveType(CqlIdentifier.fromCql("currency"))).thenReturn(currency);
	}

	@Test // DATACASS-487, DATACASS-623
	public void shouldReadMappedUdtInMap() {

		UdtValue key = manufacturer.newValue().setString("name", "a good one").setString("displayname", "my displayName");
		UdtValue value1 = currency.newValue().setString("currency", "EUR");
		UdtValue value2 = currency.newValue().setString("currency", "USD");

		Map<UdtValue, List<UdtValue>> map = new HashMap<>();

		map.put(key, Arrays.asList(value1, value2));

		rowMock = RowMockUtil
				.newRowMock(column("acceptedCurrencies", map, DataTypes.mapOf(manufacturer, DataTypes.listOf(currency))));

		Supplier supplier = mappingCassandraConverter.read(Supplier.class, rowMock);

		assertThat(supplier.getAcceptedCurrencies()).isNotEmpty();

		List<Currency> currencies = supplier.getAcceptedCurrencies().get(new Manufacturer("a good one", "my displayName"));

		assertThat(currencies).contains(new Currency("EUR"), new Currency("USD"));
	}

	@Test // DATACASS-487, DATACASS-623
	public void shouldWriteMappedUdtInMap() {

		Map<Manufacturer, List<Currency>> currencies = Collections.singletonMap(new Manufacturer("a good one", "foo"),
				Arrays.asList(new Currency("EUR"), new Currency("USD")));

		Supplier supplier = new Supplier(currencies);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(supplier, insert);

		assertThat(insert.toString())
				.contains("VALUES ({{name:'a good one',displayname:NULL}:[{currency:'EUR'},{currency:'USD'}]}");
	}

	@UserDefinedType
	@Data
	@AllArgsConstructor
	private static class Manufacturer {

		String name;
		@ReadOnlyProperty String displayName;
	}

	@UserDefinedType
	@Data
	@AllArgsConstructor
	private static class Currency {
		String currency;
	}

	@Data
	@AllArgsConstructor
	private static class Supplier {
		Map<Manufacturer, List<Currency>> acceptedCurrencies;
	}
}
