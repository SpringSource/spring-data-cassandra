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

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.cassandra.core.mapping.BasicMapId.*;
import static org.springframework.data.cassandra.test.util.RowMockUtil.*;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import org.junit.Before;
import org.junit.Test;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.BasicMapId;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraSimpleTypeHolder;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.MapId;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.domain.CompositeKey;
import org.springframework.data.cassandra.domain.TypeWithCompositeKey;
import org.springframework.data.cassandra.domain.TypeWithKeyClass;
import org.springframework.data.cassandra.domain.TypeWithMapId;
import org.springframework.data.cassandra.domain.User;
import org.springframework.data.cassandra.domain.UserToken;
import org.springframework.data.cassandra.test.util.RowMockUtil;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;

/**
 * Unit tests for {@link MappingCassandraConverter}.
 *
 * @author Mark Paluch
 * @soundtrack Outlandich - Dont Leave Me Feat Cyt (Sun Kidz Electrocore Mix)
 */
public class MappingCassandraConverterUnitTests {

	Row rowMock;

	CassandraMappingContext mappingContext;
	MappingCassandraConverter mappingCassandraConverter;

	@Before
	public void setUp() {

		this.mappingContext = new CassandraMappingContext();

		this.mappingCassandraConverter = new MappingCassandraConverter(mappingContext);
		this.mappingCassandraConverter.afterPropertiesSet();
	}

	@Test // DATACASS-260
	public void insertEnumShouldMapToString() {

		WithEnumColumns withEnumColumns = new WithEnumColumns();

		withEnumColumns.setCondition(Condition.MINT);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(withEnumColumns, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-255
	public void insertEnumMapsToOrdinal() {

		EnumToOrdinalMapping enumToOrdinalMapping = new EnumToOrdinalMapping();
		enumToOrdinalMapping.setAsOrdinal(Condition.USED);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(enumToOrdinalMapping, insert);

		assertThat(getValues(insert)).contains(Integer.valueOf(Condition.USED.ordinal()));
	}

	@Test // DATACASS-255, DATACASS-652
	public void selectEnumMapsToOrdinal() {

		rowMock = RowMockUtil.newRowMock(column("asOrdinal", 1, DataTypes.INT));

		EnumToOrdinalMapping loaded = mappingCassandraConverter.read(EnumToOrdinalMapping.class, rowMock);

		assertThat(loaded.getAsOrdinal()).isEqualTo(Condition.USED);
	}

	@Test // DATACASS-260
	public void insertEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(key, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-260
	public void insertEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(composite, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-260
	public void updateEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Where where = new Where();

		mappingCassandraConverter.write(key, where);

		assertThat(getWhereValues(where)).contains("MINT");
	}

	@Test // DATACASS-260
	public void writeWhereEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Where where = new Where();

		mappingCassandraConverter.write(composite, where);

		assertThat(getWhereValues(where)).contains("MINT");
	}

	@Test // DATACASS-260
	public void writeWhereEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Where where = new Where();

		mappingCassandraConverter.write(key, where);

		assertThat(getWhereValues(where)).contains("MINT");
	}

	@Test // DATACASS-280
	public void shouldReadStringCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", "foo", DataTypes.TEXT));

		String result = mappingCassandraConverter.readRow(String.class, rowMock);

		assertThat(result).isEqualTo("foo");
	}

	@Test // DATACASS-280
	public void shouldReadIntegerCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2, DataTypes.VARINT));

		Integer result = mappingCassandraConverter.readRow(Integer.class, rowMock);

		assertThat(result).isEqualTo(2);
	}

	@Test // DATACASS-280
	public void shouldReadLongCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2, DataTypes.VARINT));

		Long result = mappingCassandraConverter.readRow(Long.class, rowMock);

		assertThat(result).isEqualTo(2L);
	}

	@Test // DATACASS-280
	public void shouldReadDoubleCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2D, DataTypes.DOUBLE));

		Double result = mappingCassandraConverter.readRow(Double.class, rowMock);

		assertThat(result).isEqualTo(2D);
	}

	@Test // DATACASS-280
	public void shouldReadFloatCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2F, DataTypes.DOUBLE));

		Float result = mappingCassandraConverter.readRow(Float.class, rowMock);

		assertThat(result).isEqualTo(2F);
	}

	@Test // DATACASS-280
	public void shouldReadBigIntegerCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", BigInteger.valueOf(2), DataTypes.BIGINT));

		BigInteger result = mappingCassandraConverter.readRow(BigInteger.class, rowMock);

		assertThat(result).isEqualTo(BigInteger.valueOf(2));
	}

	@Test // DATACASS-280
	public void shouldReadBigDecimalCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", BigDecimal.valueOf(2), DataTypes.DECIMAL));

		BigDecimal result = mappingCassandraConverter.readRow(BigDecimal.class, rowMock);

		assertThat(result).isEqualTo(BigDecimal.valueOf(2));
	}

	@Test // DATACASS-280
	public void shouldReadUUIDCorrectly() {

		UUID uuid = UUID.randomUUID();

		rowMock = RowMockUtil.newRowMock(column("foo", uuid, DataTypes.UUID));

		UUID result = mappingCassandraConverter.readRow(UUID.class, rowMock);

		assertThat(result).isEqualTo(uuid);
	}

	@Test // DATACASS-280
	public void shouldReadInetAddressCorrectly() throws UnknownHostException {

		InetAddress localHost = InetAddress.getLocalHost();

		rowMock = RowMockUtil.newRowMock(column("foo", localHost, DataTypes.UUID));

		InetAddress result = mappingCassandraConverter.readRow(InetAddress.class, rowMock);

		assertThat(result).isEqualTo(localHost);
	}

	@Test // DATACASS-280, DATACASS-271
	public void shouldReadTimestampCorrectly() {

		Instant instant = Instant.now();

		rowMock = RowMockUtil.newRowMock(column("foo", instant, DataTypes.TIMESTAMP));

		Date result = mappingCassandraConverter.readRow(Date.class, rowMock);

		assertThat(result).isEqualTo(Date.from(instant));
	}

	@Test // DATACASS-280, DATACASS-271
	public void shouldReadInstantTimestampCorrectly() {

		Instant instant = Instant.now();

		rowMock = RowMockUtil.newRowMock(column("foo", instant, DataTypes.TIMESTAMP));

		Instant result = mappingCassandraConverter.readRow(Instant.class, rowMock);

		assertThat(result).isEqualTo(instant);
	}

	@Test // DATACASS-271
	public void shouldReadDateCorrectly() {

		LocalDate date = LocalDate.ofEpochDay(1234);

		rowMock = RowMockUtil.newRowMock(column("foo", date, DataTypes.DATE));

		LocalDate result = mappingCassandraConverter.readRow(LocalDate.class, rowMock);

		assertThat(result).isEqualTo(date);
	}

	@Test // DATACASS-280
	public void shouldReadBooleanCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", true, DataTypes.BOOLEAN));

		Boolean result = mappingCassandraConverter.readRow(Boolean.class, rowMock);

		assertThat(result).isEqualTo(true);
	}

	@Test // DATACASS-296
	public void shouldReadLocalDateCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localdate", Date.from(instant), DataTypes.TIMESTAMP));

		TypeWithLocalDate result = mappingCassandraConverter.readRow(TypeWithLocalDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(now.getYear());
		assertThat(result.localDate.getMonthValue()).isEqualTo(now.getMonthValue());
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithLocalDateCorrectly() {

		java.time.LocalDate now = java.time.LocalDate.now();

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.localDate = now;

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithLocalDateCorrectly() {

		java.time.LocalDate now = java.time.LocalDate.now();

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.localDate = now;

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithLocalDateListUsingCassandraDate() {

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate localDate = java.time.LocalDate.of(2010, 7, 4);

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.list = Arrays.asList(now, localDate);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		List<LocalDate> dates = (List) insert.get(CqlIdentifier.fromCql("list"));

		assertThat(dates).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
		assertThat(dates).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithLocalDateSetUsingCassandraDate() {

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate localDate = java.time.LocalDate.of(2010, 7, 4);

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.set = new HashSet<>(Arrays.asList(now, localDate));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		Set<LocalDate> dates = (Set) insert.get(CqlIdentifier.fromInternal("set"));

		assertThat(dates).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
		assertThat(dates).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldReadLocalDateTimeUsingCassandraDateCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDate", LocalDate.of(2010, 7, 4), DataTypes.DATE));

		TypeWithLocalDateMappedToDate result = mappingCassandraConverter.readRow(TypeWithLocalDateMappedToDate.class,
				rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthValue()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296, DATACASS-400
	public void shouldCreateInsertWithLocalDateUsingCassandraDateCorrectly() {

		TypeWithLocalDateMappedToDate typeWithLocalDate = new TypeWithLocalDateMappedToDate(null,
				java.time.LocalDate.of(2010, 7, 4));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithLocalDateUsingCassandraDateCorrectly() {

		TypeWithLocalDateMappedToDate typeWithLocalDate = new TypeWithLocalDateMappedToDate(null,
				java.time.LocalDate.of(2010, 7, 4));

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getValues(update)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldReadLocalDateTimeCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDateTime", Date.from(instant), DataTypes.TIMESTAMP));

		TypeWithLocalDate result = mappingCassandraConverter.readRow(TypeWithLocalDate.class, rowMock);

		assertThat(result.localDateTime).isNotNull();
		assertThat(result.localDateTime.getYear()).isEqualTo(now.getYear());
		assertThat(result.localDateTime.getMinute()).isEqualTo(now.getMinute());
	}

	@Test // DATACASS-296
	public void shouldReadInstantCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("instant", Date.from(instant), DataTypes.TIMESTAMP));

		TypeWithInstant result = mappingCassandraConverter.readRow(TypeWithInstant.class, rowMock);

		assertThat(result.instant).isNotNull();
		assertThat(result.instant.getEpochSecond()).isEqualTo(instant.getEpochSecond());
	}

	@Test // DATACASS-296
	public void shouldReadZoneIdCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("zoneId", "Europe/Paris", DataTypes.TEXT));

		TypeWithZoneId result = mappingCassandraConverter.readRow(TypeWithZoneId.class, rowMock);

		assertThat(result.zoneId).isNotNull();
		assertThat(result.zoneId.getId()).isEqualTo("Europe/Paris");
	}

	@Test // DATACASS-296
	public void shouldReadJodaLocalDateTimeUsingCassandraDateCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDate", LocalDate.of(2010, 7, 4), DataTypes.DATE));

		TypeWithJodaLocalDateMappedToDate result = mappingCassandraConverter
				.readRow(TypeWithJodaLocalDateMappedToDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthOfYear()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithJodaLocalDateUsingCassandraDateCorrectly() {

		TypeWithJodaLocalDateMappedToDate typeWithLocalDate = new TypeWithJodaLocalDateMappedToDate();
		typeWithLocalDate.localDate = new org.joda.time.LocalDate(2010, 7, 4);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithJodaLocalDateUsingCassandraDateCorrectly() {

		TypeWithJodaLocalDateMappedToDate typeWithLocalDate = new TypeWithJodaLocalDateMappedToDate();
		typeWithLocalDate.localDate = new org.joda.time.LocalDate(2010, 7, 4);

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getValues(update)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldReadThreeTenBpLocalDateTimeUsingCassandraDateCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDate", LocalDate.of(2010, 7, 4), DataTypes.DATE));

		TypeWithThreeTenBpLocalDateMappedToDate result = mappingCassandraConverter
				.readRow(TypeWithThreeTenBpLocalDateMappedToDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthValue()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296
	public void shouldCreateInsertWithThreeTenBpLocalDateUsingCassandraDateCorrectly() {

		TypeWithThreeTenBpLocalDateMappedToDate typeWithLocalDate = new TypeWithThreeTenBpLocalDateMappedToDate();
		typeWithLocalDate.localDate = org.threeten.bp.LocalDate.of(2010, 7, 4);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	public void shouldCreateUpdateWithThreeTenBpLocalDateUsingCassandraDateCorrectly() {

		TypeWithThreeTenBpLocalDateMappedToDate typeWithLocalDate = new TypeWithThreeTenBpLocalDateMappedToDate();
		typeWithLocalDate.localDate = org.threeten.bp.LocalDate.of(2010, 7, 4);

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getValues(update)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-206
	public void updateShouldUseSpecifiedColumnNames() {

		UserToken userToken = new UserToken();
		userToken.setUserId(UUID.randomUUID());
		userToken.setToken(UUID.randomUUID());
		userToken.setAdminComment("admin comment");
		userToken.setUserComment("user comment");

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();
		Where where = new Where();

		mappingCassandraConverter.write(userToken, update);
		mappingCassandraConverter.write(userToken, where);

		assertThat(update).containsEntry(CqlIdentifier.fromCql("admincomment"), "admin comment");
		assertThat(update).containsEntry(CqlIdentifier.fromCql("user_comment"), "user comment");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("user_id"), userToken.getUserId());
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionUsingPlainId() {

		Where where = new Where();

		mappingCassandraConverter.write("42", where, mappingContext.getRequiredPersistentEntity(User.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("id"), "42");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionUsingEntity() {

		Where where = new Where();

		User user = new User();
		user.setId("42");

		mappingCassandraConverter.write(user, where, mappingContext.getRequiredPersistentEntity(User.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("id"), "42");
	}

	@Test(expected = IllegalArgumentException.class) // DATACASS-308
	public void shouldFailWriteWhereConditionUsingEntityWithNullId() {

		mappingCassandraConverter.write(new User(), new Where(), mappingContext.getRequiredPersistentEntity(User.class));
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("id", "42"), where, mappingContext.getRequiredPersistentEntity(User.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("id"), "42");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForCompositeKeyUsingEntity() {

		Where where = new Where();

		TypeWithCompositeKey entity = new TypeWithCompositeKey();
		entity.setFirstname("Walter");
		entity.setLastname("White");

		mappingCassandraConverter.write(entity, where,
				mappingContext.getRequiredPersistentEntity(TypeWithCompositeKey.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForCompositeKeyUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), where,
				mappingContext.getRequiredPersistentEntity(TypeWithCompositeKey.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForMapIdKeyUsingEntity() {

		Where where = new Where();

		TypeWithMapId entity = new TypeWithMapId();
		entity.setFirstname("Walter");
		entity.setLastname("White");

		mappingCassandraConverter.write(entity, where, mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	public void shouldWriteEnumWhereCondition() {

		Where where = new Where();

		mappingCassandraConverter.write(Condition.MINT, where,
				mappingContext.getRequiredPersistentEntity(EnumPrimaryKey.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("condition"), "MINT");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForMapIdKeyUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), where,
				mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForTypeWithPkClassKeyUsingEntity() {

		Where where = new Where();

		CompositeKey key = new CompositeKey();
		key.setFirstname("Walter");
		key.setLastname("White");

		TypeWithKeyClass entity = new TypeWithKeyClass();
		entity.setKey(key);

		mappingCassandraConverter.write(entity, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test(expected = IllegalArgumentException.class) // DATACASS-308
	public void shouldFailWritingWhereConditionForTypeWithPkClassKeyUsingEntityWithNullId() {

		mappingCassandraConverter.write(new TypeWithKeyClass(), new Where(),
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForTypeWithPkClassKeyUsingKey() {

		Where where = new Where();

		CompositeKey key = new CompositeKey();
		key.setFirstname("Walter");
		key.setLastname("White");

		mappingCassandraConverter.write(key, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-463
	public void shouldReadTypeWithCompositePrimaryKeyCorrectly() {

		// condition, localDate
		Row row = RowMockUtil.newRowMock(column("condition", "MINT", DataTypes.TEXT),
				column("localdate", LocalDate.of(2017, 1, 2), DataTypes.DATE));

		TypeWithEnumAndLocalDateKey result = mappingCassandraConverter.read(TypeWithEnumAndLocalDateKey.class, row);

		assertThat(result.id.condition).isEqualTo(Condition.MINT);
		assertThat(result.id.localDate).isEqualTo(java.time.LocalDate.of(2017, 1, 2));
	}

	@Test // DATACASS-672
	public void shouldReadTypeCompositePrimaryKeyUsingEntityInstantiatorAndPropertyPopulationInKeyCorrectly() {

		// condition, localDate
		Row row = RowMockUtil.newRowMock(column("firstname", "Walter", DataTypes.TEXT),
				column("lastname", "White", DataTypes.TEXT));

		TableWithCompositeKeyViaConstructor result = mappingCassandraConverter
				.read(TableWithCompositeKeyViaConstructor.class, row);

		assertThat(result.key.firstname).isEqualTo("Walter");
		assertThat(result.key.lastname).isEqualTo("White");
	}

	@Test // DATACASS-308
	public void shouldWriteWhereConditionForTypeWithPkClassKeyUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), where,
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test(expected = IllegalArgumentException.class) // DATACASS-308
	public void shouldFailWhereConditionForTypeWithPkClassKeyUsingMapIdHavingUnknownProperty() {

		mappingCassandraConverter.write(id("unknown", "Walter"), new Where(),
				mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));
	}

	@Test // DATACASS-362
	public void shouldWriteWhereCompositeIdUsingCompositeKeyClass() {

		Where where = new Where();

		CompositeKey key = new CompositeKey();
		key.setFirstname("first");
		key.setLastname("last");

		mappingCassandraConverter.write(key, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "first");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "last");
	}

	@Test // DATACASS-362
	public void writeWhereCompositeIdUsingCompositeKeyClassViaMapId() {

		Where where = new Where();

		MapId mapId = BasicMapId.id("firstname", "first").with("lastname", "last");

		mappingCassandraConverter.write(mapId, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "first");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "last");
	}

	@Test // DATACASS-487
	public void shouldReadConvertedMap() {

		LocalDate date1 = LocalDate.of(2018, 1, 1);
		LocalDate date2 = LocalDate.of(2019, 1, 1);

		Map<String, List<LocalDate>> times = Collections.singletonMap("Europe/Paris", Arrays.asList(date1, date2));

		rowMock = RowMockUtil.newRowMock(
				RowMockUtil.column("times", times, DataTypes.mapOf(DataTypes.TEXT, DataTypes.listOf(DataTypes.DATE))));

		TypeWithConvertedMap converted = this.mappingCassandraConverter.read(TypeWithConvertedMap.class, rowMock);

		assertThat(converted.times).containsKeys(ZoneId.of("Europe/Paris"));

		List<java.time.LocalDate> convertedTimes = converted.times.get(ZoneId.of("Europe/Paris"));

		assertThat(convertedTimes).hasSize(2).hasOnlyElementsOfType(java.time.LocalDate.class);
	}

	@Test // DATACASS-487
	public void shouldWriteConvertedMap() {

		java.time.LocalDate date1 = java.time.LocalDate.of(2018, 1, 1);
		java.time.LocalDate date2 = java.time.LocalDate.of(2019, 1, 1);

		TypeWithConvertedMap typeWithConvertedMap = new TypeWithConvertedMap();

		typeWithConvertedMap.times = Collections.singletonMap(ZoneId.of("Europe/Paris"), Arrays.asList(date1, date2));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(typeWithConvertedMap, insert);

		List<Object> values = getValues(insert);

		assertThat(values).isNotEmpty();
		assertThat(values.get(1)).isInstanceOf(Map.class);

		Map<String, List<LocalDate>> map = (Map) values.get(1);

		assertThat(map).containsKey("Europe/Paris");
		assertThat(map.get("Europe/Paris")).hasOnlyElementsOfType(LocalDate.class);
	}

	@Test // DATACASS-189
	public void writeShouldSkipTransientProperties() {

		WithTransient withTransient = new WithTransient();
		withTransient.firstname = "Foo";
		withTransient.lastname = "Bar";
		withTransient.displayName = "FooBar";

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(withTransient, insert);

		assertThat(insert).containsKey(CqlIdentifier.fromCql("firstname"))
				.doesNotContainKey(CqlIdentifier.fromCql("displayName"));
	}

	@Test // DATACASS-623
	public void writeShouldSkipTransientReadProperties() {

		WithTransient withTransient = new WithTransient();
		withTransient.firstname = "Foo";
		withTransient.computedName = "FooBar";

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(withTransient, insert);

		assertThat(insert).containsKey(CqlIdentifier.fromCql("firstname"))
				.doesNotContainKey(CqlIdentifier.fromCql("computedName"));
	}

	private static List<Object> getValues(Map<CqlIdentifier, Object> statement) {
		return new ArrayList<>(statement.values());
	}

	private static Collection<Object> getWhereValues(Where update) {
		return update.values();
	}

	@Table
	public static class EnumToOrdinalMapping {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraSimpleTypeHolder.Name.INT) private Condition asOrdinal;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Condition getAsOrdinal() {
			return asOrdinal;
		}

		public void setAsOrdinal(Condition asOrdinal) {
			this.asOrdinal = asOrdinal;
		}
	}

	@Table
	public static class WithEnumColumns {

		@PrimaryKey private String id;

		private Condition condition;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Condition getCondition() {
			return condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@PrimaryKeyClass
	public static class EnumCompositePrimaryKey implements Serializable {

		@PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.PARTITIONED) private Condition condition;

		public EnumCompositePrimaryKey() {}

		public EnumCompositePrimaryKey(Condition condition) {
			this.condition = condition;
		}

		public Condition getCondition() {
			return condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@PrimaryKeyClass
	@RequiredArgsConstructor
	@Value
	public static class EnumAndDateCompositePrimaryKey implements Serializable {

		@PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.PARTITIONED) private final Condition condition;

		@PrimaryKeyColumn(ordinal = 2, type = PrimaryKeyType.PARTITIONED) private final java.time.LocalDate localDate;
	}

	@RequiredArgsConstructor
	public static class TypeWithEnumAndLocalDateKey {

		@PrimaryKey private final EnumAndDateCompositePrimaryKey id;
	}

	@Table
	public static class EnumPrimaryKey {

		@PrimaryKey private Condition condition;

		public Condition getCondition() {
			return condition;
		}

		public void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@Table
	public static class CompositeKeyThing {

		@PrimaryKey private EnumCompositePrimaryKey key;

		public CompositeKeyThing() {}

		public CompositeKeyThing(EnumCompositePrimaryKey key) {
			this.key = key;
		}

		public EnumCompositePrimaryKey getKey() {
			return key;
		}

		public void setKey(EnumCompositePrimaryKey key) {
			this.key = key;
		}
	}

	public enum Condition {
		MINT, USED
	}

	@PrimaryKeyClass
	public static class CompositeKeyWithPropertyAccessors {

		@PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED) String firstname;
		@PrimaryKeyColumn String lastname;
	}

	@Table
	@RequiredArgsConstructor
	public static class TableWithCompositeKeyViaConstructor {

		@PrimaryKey private final CompositeKeyWithPropertyAccessors key;
	}

	@Table
	public static class TypeWithLocalDate {

		@PrimaryKey private String id;

		java.time.LocalDate localDate;
		java.time.LocalDateTime localDateTime;

		List<java.time.LocalDate> list;
		Set<java.time.LocalDate> set;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to {@link LocalDate}
	 */
	@Table
	@AllArgsConstructor
	public static class TypeWithLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraSimpleTypeHolder.Name.DATE) java.time.LocalDate localDate;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to Joda {@link LocalDate}
	 */
	@Table
	public static class TypeWithJodaLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraSimpleTypeHolder.Name.DATE) org.joda.time.LocalDate localDate;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to Joda {@link LocalDate}
	 */
	@Table
	public static class TypeWithThreeTenBpLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraSimpleTypeHolder.Name.DATE) org.threeten.bp.LocalDate localDate;
	}

	@Table
	public static class TypeWithInstant {

		@PrimaryKey private String id;

		Instant instant;
	}

	@Table
	public static class TypeWithZoneId {

		@PrimaryKey private String id;

		ZoneId zoneId;
	}

	@Table
	public static class TypeWithConvertedMap {

		@PrimaryKey private String id;

		Map<ZoneId, List<java.time.LocalDate>> times;
	}

	static class WithTransient {

		@Id String id;

		String firstname;
		String lastname;
		@Transient String displayName;
		@ReadOnlyProperty String computedName;
	}
}
