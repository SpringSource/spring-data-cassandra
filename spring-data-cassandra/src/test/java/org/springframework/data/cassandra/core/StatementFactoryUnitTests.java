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

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.convert.UpdateMapper;
import org.springframework.data.cassandra.core.cql.WriteOptions;
import org.springframework.data.cassandra.core.cql.util.StatementBuilder;
import org.springframework.data.cassandra.core.cql.util.StatementBuilder.ParameterHandling;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.query.Columns;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.cassandra.core.query.Update;
import org.springframework.data.cassandra.domain.Group;
import org.springframework.data.domain.Sort;

import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.select.Select;

/**
 * Unit tests for {@link StatementFactory}.
 *
 * @author Mark Paluch
 */
public class StatementFactoryUnitTests {

	CassandraConverter converter = new MappingCassandraConverter();

	UpdateMapper updateMapper = new UpdateMapper(converter);

	StatementFactory statementFactory = new StatementFactory(updateMapper, updateMapper);

	CassandraPersistentEntity<?> groupEntity = converter.getMappingContext().getRequiredPersistentEntity(Group.class);
	CassandraPersistentEntity<?> personEntity = converter.getMappingContext().getRequiredPersistentEntity(Person.class);

	@Test // DATACASS-343
	public void shouldMapSimpleSelectQuery() {

		StatementBuilder<Select> select = statementFactory.select(Query.empty(),
				converter.getMappingContext().getRequiredPersistentEntity(Group.class));

		assertThat(select.build(ParameterHandling.INLINE).getQuery()).isEqualTo("SELECT * FROM group");
	}

	@Test // DATACASS-343
	public void shouldMapSelectQueryWithColumnsAndCriteria() {

		Query query = Query.query(Criteria.where("foo").is("bar")).columns(Columns.from("age"));

		StatementBuilder<Select> select = statementFactory.select(query, groupEntity);

		assertThat(select.build(ParameterHandling.INLINE).getQuery()).isEqualTo("SELECT age FROM group WHERE foo='bar'");
	}

	@Test // DATACASS-549
	public void shouldMapSelectQueryNotEquals() {

		Query query = Query.query(Criteria.where("foo").ne("bar")).columns(Columns.from("age"));

		StatementBuilder<Select> select = statementFactory.select(query, groupEntity);

		assertThat(select.build(ParameterHandling.INLINE).getQuery()).isEqualTo("SELECT age FROM group WHERE foo!='bar'");
	}

	@Test // DATACASS-549
	public void shouldMapSelectQueryIsNotNull() {

		Query query = Query.query(Criteria.where("foo").isNotNull()).columns(Columns.from("age"));

		StatementBuilder<Select> select = statementFactory.select(query, groupEntity);

		assertThat(select.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("SELECT age FROM group WHERE foo IS NOT NULL");
	}

	@Test // DATACASS-343
	public void shouldMapSelectQueryWithTtlColumns() {

		Query query = Query.empty().columns(Columns.empty().ttl("email"));

		StatementBuilder<Select> select = statementFactory.select(query,
				converter.getMappingContext().getRequiredPersistentEntity(Group.class));

		assertThat(select.build(ParameterHandling.INLINE).getQuery()).isEqualTo("SELECT ttl(email) FROM group");
	}

	@Test // DATACASS-343
	public void shouldMapSelectQueryWithSortLimitAndAllowFiltering() {

		Query query = Query.empty().sort(Sort.by("id.hashPrefix")).limit(10).withAllowFiltering();

		StatementBuilder<Select> select = statementFactory.select(query,
				converter.getMappingContext().getRequiredPersistentEntity(Group.class));

		assertThat(select.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("SELECT * FROM group ORDER BY hash_prefix ASC LIMIT 10 ALLOW FILTERING");
	}

	@Test // DATACASS-343
	public void shouldMapDeleteQueryWithColumns() {

		Query query = Query.empty().columns(Columns.from("age"));

		StatementBuilder<Delete> delete = statementFactory.delete(query,
				converter.getMappingContext().getRequiredPersistentEntity(Group.class));

		assertThat(delete.build(ParameterHandling.INLINE).getQuery()).isEqualTo("DELETE age FROM group");
	}

	@Test // DATACASS-343
	public void shouldMapDeleteQueryWithTimestampColumns() {

		DeleteOptions options = DeleteOptions.builder().timestamp(1234).build();
		Query query = Query.query(Criteria.where("foo").is("bar")).queryOptions(options);

		StatementBuilder<Delete> delete = statementFactory.delete(query,
				converter.getMappingContext().getRequiredPersistentEntity(Group.class));

		assertThat(delete.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("DELETE FROM group USING TIMESTAMP 1234 WHERE foo='bar'");
	}

	@Test // DATACASS-656
	public void shouldCreateInsert() {

		Person person = new Person();
		person.id = "foo";

		StatementBuilder<RegularInsert> insert = statementFactory.insert(person, WriteOptions.empty());

		assertThat(insert.build(ParameterHandling.INLINE).getQuery()).isEqualTo("INSERT INTO person (id) VALUES ('foo')");
	}

	@Test // DATACASS-656
	public void shouldCreateInsertIfNotExists() {

		InsertOptions options = InsertOptions.builder().withIfNotExists().build();
		Person person = new Person();
		person.id = "foo";

		StatementBuilder<RegularInsert> insert = statementFactory.insert(person, options);

		assertThat(insert.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("INSERT INTO person (id) VALUES ('foo') IF NOT EXISTS");
	}

	@Test // DATACASS-656
	public void shouldCreateSetInsertNulls() {

		InsertOptions options = InsertOptions.builder().withInsertNulls().build();
		Person person = new Person();
		person.id = "foo";

		StatementBuilder<RegularInsert> insert = statementFactory.insert(person, options);

		assertThat(insert.build(ParameterHandling.INLINE).getQuery()).isEqualTo(
				"INSERT INTO person (first_name,id,list,map,number,set_col) VALUES (NULL,'foo',NULL,NULL,NULL,NULL)");
	}

	@Test // DATACASS-656
	public void shouldCreateSetInsertWithTtl() {

		WriteOptions options = WriteOptions.builder().ttl(Duration.ofMinutes(1)).build();
		Person person = new Person();
		person.id = "foo";

		StatementBuilder<RegularInsert> insert = statementFactory.insert(person, options);

		assertThat(insert.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("INSERT INTO person (id) VALUES ('foo') USING TTL 60");
	}

	@Test // DATACASS-656
	public void shouldCreateSetInsertWithTimestamp() {

		WriteOptions options = WriteOptions.builder().timestamp(1234).build();
		Person person = new Person();
		person.id = "foo";

		StatementBuilder<RegularInsert> insert = statementFactory.insert(person, options);

		assertThat(insert.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("INSERT INTO person (id) VALUES ('foo') USING TIMESTAMP 1234");
	}

	@Test // DATACASS-343
	public void shouldCreateSetUpdate() {

		Query query = Query.query(Criteria.where("foo").is("bar"));

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(query,
				Update.empty().set("firstName", "baz").set("boo", "baa"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET first_name='baz', boo='baa' WHERE foo='bar'");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateWithTtl() {

		WriteOptions options = WriteOptions.builder().ttl(Duration.ofMinutes(1)).build();
		Query query = Query.query(Criteria.where("foo").is("bar")).queryOptions(options);

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(query,
				Update.empty().set("firstName", "baz"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person USING TTL 60 SET first_name='baz' WHERE foo='bar'");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateWithTimestamp() {

		WriteOptions options = WriteOptions.builder().timestamp(1234).build();
		Query query = Query.query(Criteria.where("foo").is("bar")).queryOptions(options);

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(query,
				Update.empty().set("firstName", "baz"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person USING TIMESTAMP 1234 SET first_name='baz' WHERE foo='bar'");
	}

	@Test // DATACASS-343
	@Ignore("No operator for set at index yet")
	public void shouldCreateSetAtIndexUpdate() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().set("list").atIndex(10).to("Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET list[10]='Euro'");
	}

	@Test // DATACASS-343
	public void shouldCreateSetAtKeyUpdate() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().set("map").atKey("baz").to("Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET map['baz']='Euro'");
	}

	@Test // DATACASS-343
	public void shouldAddToMap() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().addTo("map").entry("foo", "Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET map+={'foo':'Euro'}");
	}

	@Test // DATACASS-343
	public void shouldPrependAllToList() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().addTo("list").prependAll("foo", "Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET list=['foo','Euro']+list");
	}

	@Test // DATACASS-343
	public void shouldAppendAllToList() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().addTo("list").appendAll("foo", "Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET list+=['foo','Euro']");
	}

	@Test // DATACASS-343
	public void shouldRemoveFromList() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().remove("list", "Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET list-=['Euro']");
	}

	@Test // DATACASS-343
	public void shouldClearList() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().clear("list"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET list=[]");
	}

	@Test // DATACASS-343
	public void shouldAddAllToSet() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().addTo("set").appendAll("foo", "Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET set_col+={'foo','Euro'}");
	}

	@Test // DATACASS-343
	public void shouldRemoveFromSet() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().remove("set", "Euro"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET set_col-={'Euro'}");
	}

	@Test // DATACASS-343
	public void shouldClearSet() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().clear("set"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET set_col={}");
	}

	@Test // DATACASS-343
	public void shouldCreateIncrementUpdate() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().increment("number"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET number+=1");
	}

	@Test // DATACASS-343
	public void shouldCreateDecrementUpdate() {

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory
				.update(Query.empty(), Update.empty().decrement("number"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).isEqualTo("UPDATE person SET number-=1");
	}

	@Test // DATACASS-569
	public void shouldCreateSetUpdateIfExists() {

		Query query = Query.query(Criteria.where("foo").is("bar"))
				.queryOptions(UpdateOptions.builder().withIfExists().build());

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(query,
				Update.empty().set("firstName", "baz"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET first_name='baz' WHERE foo='bar' IF EXISTS");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateIfCondition() {

		Query query = Query.query(Criteria.where("foo").is("bar"))
				.queryOptions(UpdateOptions.builder().ifCondition(Criteria.where("foo").is("baz")).build());

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(query,
				Update.empty().set("firstName", "baz"), personEntity);

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET first_name='baz' WHERE foo='bar' IF foo='baz'");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateFromObject() {

		Person person = new Person();
		person.id = "foo";
		person.firstName = "bar";

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(person,
				WriteOptions.empty());

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET first_name='bar', list=NULL, map=NULL, number=NULL, set_col=NULL WHERE id='foo'");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateFromObjectIfExists() {

		UpdateOptions options = UpdateOptions.builder().withIfExists().build();
		Person person = new Person();
		person.id = "foo";
		person.firstName = "bar";

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(person,
				options);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).endsWith("IF EXISTS");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateFromObjectIfCondition() {

		UpdateOptions options = UpdateOptions.builder().ifCondition(Criteria.where("foo").is("bar")).build();
		Person person = new Person();
		person.id = "foo";
		person.firstName = "bar";

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(person,
				options);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).endsWith("IF foo='bar'");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateFromObjectWithTtl() {

		WriteOptions options = WriteOptions.builder().ttl(Duration.ofMinutes(1)).build();
		Person person = new Person();
		person.id = "foo";

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(person,
				options);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).startsWith("UPDATE person USING TTL 60 SET");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateFromObjectWithTimestamp() {

		WriteOptions options = WriteOptions.builder().timestamp(1234).build();
		Person person = new Person();
		person.id = "foo";

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(person,
				options);

		assertThat(update.build(ParameterHandling.INLINE).getQuery()).startsWith("UPDATE person USING TIMESTAMP 1234 SET");
	}

	@Test // DATACASS-656
	public void shouldCreateSetUpdateFromObjectWithEmptyCollections() {

		Person person = new Person();
		person.id = "foo";
		person.set = Collections.emptySet();
		person.list = Collections.emptyList();

		StatementBuilder<com.datastax.oss.driver.api.querybuilder.update.Update> update = statementFactory.update(person,
				WriteOptions.empty());

		assertThat(update.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("UPDATE person SET first_name=NULL, list=[], map=NULL, number=NULL, set_col={} WHERE id='foo'");
	}

	@Test // DATACASS-512
	public void shouldCreateCountQuery() {

		Query query = Query.query(Criteria.where("foo").is("bar"));

		StatementBuilder<Select> count = statementFactory.count(query,
				converter.getMappingContext().getRequiredPersistentEntity(Group.class));

		assertThat(count.build(ParameterHandling.INLINE).getQuery())
				.isEqualTo("SELECT count(1) FROM group WHERE foo='bar'");
	}

	static class Person {

		@Id String id;

		List<String> list;
		@Column("set_col") Set<String> set;
		Map<String, String> map;

		Integer number;

		@Column("first_name") String firstName;
	}
}
