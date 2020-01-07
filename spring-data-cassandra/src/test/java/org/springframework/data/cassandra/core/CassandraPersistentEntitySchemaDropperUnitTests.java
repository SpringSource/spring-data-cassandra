/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.data.cassandra.core;

import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.support.UserDefinedTypeBuilder;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;

/**
 * Unit tests for {@link CassandraPersistentEntitySchemaDropper}.
 *
 * @author Mark Paluch.
 */
@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class CassandraPersistentEntitySchemaDropperUnitTests extends CassandraPersistentEntitySchemaTestSupport {

	@Mock CassandraAdminOperations operations;
	@Mock KeyspaceMetadata metadata;
	UserDefinedType universetype = UserDefinedTypeBuilder.forName("universetype").withField("name", DataTypes.TEXT)
			.build();
	UserDefinedType moontype = UserDefinedTypeBuilder.forName("moontype").withField("universeType", universetype).build();
	UserDefinedType planettype = UserDefinedTypeBuilder.forName("planettype")
			.withField("moonType", DataTypes.setOf(moontype)).withField("universeType", universetype).build();
	@Mock TableMetadata person;
	@Mock TableMetadata contact;

	CassandraMappingContext context = new CassandraMappingContext();

	// DATACASS-355
	@Before
	public void setUp() {

		context.setUserTypeResolver(typeName -> metadata.getUserDefinedType(typeName).get());

		when(operations.getKeyspaceMetadata()).thenReturn(metadata);
		when(person.getName()).thenReturn(CqlIdentifier.fromCql("person"));
		when(contact.getName()).thenReturn(CqlIdentifier.fromCql("contact"));
	}

	@Test // DATACASS-355, DATACASS-546
	public void shouldDropTypesInOrderOfDependencies() {

		when(metadata.getUserDefinedTypes()).thenReturn(createTypes(universetype, moontype, planettype));

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropUserTypes(true);

		verifyTypesGetDroppedInOrderFor("planettype", "moontype", "universetype");
	}

	@Test // DATACASS-355
	public void dropUserTypesShouldRetainUnusedTypes() {

		context.setInitialEntitySet(new HashSet<>(Arrays.asList(MoonType.class, UniverseType.class)));
		context.afterPropertiesSet();

		when(metadata.getUserDefinedTypes()).thenReturn(createTypes(universetype, moontype, planettype));

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropUserTypes(false);

		verify(operations).dropUserType(CqlIdentifier.fromCql("universetype"));
		verify(operations).dropUserType(CqlIdentifier.fromCql("moontype"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	@Test // DATACASS-355
	public void shouldDropTables() {

		context.setInitialEntitySet(Collections.singleton(Person.class));
		context.afterPropertiesSet();

		Map<CqlIdentifier, TableMetadata> tables = createTables(person, contact);
		when(metadata.getTables()).thenReturn(tables);

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropTables(true);

		verify(operations).dropTable(CqlIdentifier.fromCql("person"));
		verify(operations).dropTable(CqlIdentifier.fromCql("contact"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	@Test
	public void dropTablesShouldRetainUnusedTables() {

		context.setInitialEntitySet(Collections.singleton(Person.class));
		context.afterPropertiesSet();

		Map<CqlIdentifier, TableMetadata> tables = createTables(person, contact);
		when(metadata.getTables()).thenReturn(tables);

		CassandraPersistentEntitySchemaDropper schemaDropper = new CassandraPersistentEntitySchemaDropper(context,
				operations);

		schemaDropper.dropTables(false);

		verify(operations).dropTable(CqlIdentifier.fromCql("person"));
		verify(operations).getKeyspaceMetadata();
		verifyNoMoreInteractions(operations);
	}

	private void verifyTypesGetDroppedInOrderFor(String... typenames) {

		InOrder inOrder = Mockito.inOrder(operations);

		for (String typename : typenames) {
			inOrder.verify(operations).dropUserType(CqlIdentifier.fromCql(typename));
		}

		inOrder.verifyNoMoreInteractions();
	}

	private static Map<CqlIdentifier, UserDefinedType> createTypes(UserDefinedType... types) {

		Map<CqlIdentifier, UserDefinedType> result = new LinkedHashMap<>();

		Arrays.stream(types).forEach(type -> {
			result.put(type.getName(), type);
		});

		return result;
	}

	private static Map<CqlIdentifier, TableMetadata> createTables(TableMetadata... tables) {

		Map<CqlIdentifier, TableMetadata> result = new LinkedHashMap<>();

		Arrays.stream(tables).forEach(table -> {
			result.put(table.getName(), table);
		});

		return result;
	}
}
