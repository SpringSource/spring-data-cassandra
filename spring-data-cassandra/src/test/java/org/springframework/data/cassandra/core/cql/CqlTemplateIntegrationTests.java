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
package org.springframework.data.cassandra.core.cql;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assume.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.data.cassandra.CassandraInvalidQueryException;
import org.springframework.data.cassandra.support.CassandraVersion;
import org.springframework.data.cassandra.test.util.AbstractKeyspaceCreatingIntegrationTests;
import org.springframework.data.util.Version;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;

/**
 * Integration tests for {@link CqlTemplate}.
 *
 * @author Mark Paluch
 * @author Tomasz Lelek
 */
class CqlTemplateIntegrationTests extends AbstractKeyspaceCreatingIntegrationTests {
	private static final Version CASSANDRA_4 = Version.parse("4.0");
	private static final AtomicBoolean initialized = new AtomicBoolean();
	private CqlTemplate template;
	private Version cassandraVersion;

	@BeforeEach
	void before() {

		if (initialized.compareAndSet(false, true)) {
			session.execute("CREATE TABLE IF NOT EXISTS user (id text PRIMARY KEY, username text);");
		}

		session.execute("TRUNCATE user;");
		session.execute("INSERT INTO user (id, username) VALUES ('WHITE', 'Walter');");

		template = new CqlTemplate();
		template.setSession(getSession());
		cassandraVersion = CassandraVersion.get(session);
	}

	@Test // DATACASS-292
	void executeShouldRemoveRecords() {

		template.execute("DELETE FROM user WHERE id = 'WHITE'");

		assertThat(session.execute("SELECT * FROM user").one()).isNull();
	}

	@Test // DATACASS-292
	void queryShouldInvokeCallback() {

		List<String> result = new ArrayList<>();
		template.query("SELECT id FROM user;", row -> {
			result.add(row.getString(0));
		});

		assertThat(result).contains("WHITE");
	}

	@Test // DATACASS-292
	void queryForObjectShouldReturnFirstColumn() {

		String id = template.queryForObject("SELECT id FROM user;", String.class);

		assertThat(id).isEqualTo("WHITE");
	}

	@Test // DATACASS-292
	void queryForObjectShouldReturnMap() {

		Map<String, Object> map = template.queryForMap("SELECT * FROM user;");

		assertThat(map).containsEntry("id", "WHITE").containsEntry("username", "Walter");
	}

	@Test // DATACASS-292
	void executeStatementShouldRemoveRecords() {

		template.execute(SimpleStatement.newInstance("DELETE FROM user WHERE id = 'WHITE'"));

		assertThat(session.execute("SELECT * FROM user").one()).isNull();
	}

	@Test // DATACASS-292
	void queryStatementShouldInvokeCallback() {

		List<String> result = new ArrayList<>();
		template.query(SimpleStatement.newInstance("SELECT id FROM user"), row -> {
			result.add(row.getString(0));
		});

		assertThat(result).contains("WHITE");
	}

	@Test // DATACASS-292
	void queryForObjectStatementShouldReturnFirstColumn() {

		String id = template.queryForObject(SimpleStatement.newInstance("SELECT id FROM user"), String.class);

		assertThat(id).isEqualTo("WHITE");
	}

	@Test // DATACASS-292
	void queryForObjectStatementShouldReturnMap() {

		Map<String, Object> map = template.queryForMap(SimpleStatement.newInstance("SELECT * FROM user"));

		assertThat(map).containsEntry("id", "WHITE").containsEntry("username", "Walter");
	}

	@Test // DATACASS-292
	void executeWithArgsShouldRemoveRecords() {

		template.execute("DELETE FROM user WHERE id = ?", "WHITE");

		assertThat(session.execute("SELECT * FROM user").one()).isNull();
	}

	@Test // DATACASS-292
	void queryPreparedStatementShouldInvokeCallback() {

		List<String> result = new ArrayList<>();
		template.query("SELECT id FROM user WHERE id = ?;", row -> {
			result.add(row.getString(0));
		}, "WHITE");

		assertThat(result).contains("WHITE");
	}

	@Test // DATACASS-292
	void queryPreparedStatementCreatorShouldInvokeCallback() {

		List<String> result = new ArrayList<>();
		template.query(session -> session.prepare("SELECT id FROM user WHERE id = ?;"), ps -> ps.bind("WHITE"), row -> {
			result.add(row.getString(0));
		});

		assertThat(result).contains("WHITE");
	}

	@Test // DATACASS-292
	void queryForObjectWithArgsShouldReturnFirstColumn() {

		String id = template.queryForObject("SELECT id FROM user WHERE id = ?;", String.class, "WHITE");

		assertThat(id).isEqualTo("WHITE");
	}

	@Test // DATACASS-292
	void queryForObjectWithArgsShouldReturnMap() {

		Map<String, Object> map = template.queryForMap("SELECT * FROM user WHERE id = ?;", "WHITE");

		assertThat(map).containsEntry("id", "WHITE").containsEntry("username", "Walter");
	}

	@Test // DATACASS-767
	void selectByQueryWithKeyspaceShouldRetrieveData() {
		assumeTrue(cassandraVersion.isGreaterThanOrEqualTo(CASSANDRA_4));

		template.setKeyspace(CqlIdentifier.fromCql(keyspace));

		String id = template.queryForObject("SELECT id FROM user;", String.class);

		assertThat(id).isEqualTo("WHITE");
	}

	@Test // DATACASS-767
	void selectByQueryWithNonExistingKeyspaceShouldThrowThatKeyspaceDoesNotExists() {
		assumeTrue(cassandraVersion.isGreaterThanOrEqualTo(CASSANDRA_4));

		template.setKeyspace(CqlIdentifier.fromCql("non_existing"));

		assertThatThrownBy(() -> template.queryForObject("SELECT id FROM user;", String.class))
				.isInstanceOf(CassandraInvalidQueryException.class)
				.hasMessageContaining("Keyspace 'non_existing' does not exist");

	}
}
