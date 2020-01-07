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

import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;

import org.springframework.data.cassandra.ReactiveSession;
import org.springframework.data.cassandra.core.cql.session.DefaultBridgedReactiveSession;
import org.springframework.data.cassandra.core.cql.session.DefaultReactiveSessionFactory;
import org.springframework.data.cassandra.test.util.AbstractKeyspaceCreatingIntegrationTest;

import com.datastax.oss.driver.api.core.cql.SimpleStatement;

/**
 * Integration tests for {@link ReactiveCqlTemplate}.
 *
 * @author Mark Paluch
 */
public class ReactiveCqlTemplateIntegrationTests extends AbstractKeyspaceCreatingIntegrationTest {

	private static final AtomicBoolean initialized = new AtomicBoolean();

	ReactiveSession reactiveSession;
	ReactiveCqlTemplate template;

	@Before
	public void before() {

		reactiveSession = new DefaultBridgedReactiveSession(getSession());

		if (initialized.compareAndSet(false, true)) {
			getSession().execute("CREATE TABLE IF NOT EXISTS user (id text PRIMARY KEY, username text);");
		}

		getSession().execute("TRUNCATE user;");
		getSession().execute("INSERT INTO user (id, username) VALUES ('WHITE', 'Walter');");

		template = new ReactiveCqlTemplate(new DefaultReactiveSessionFactory(reactiveSession));
	}

	@Test // DATACASS-335
	public void executeShouldRemoveRecords() {

		template.execute("DELETE FROM user WHERE id = 'WHITE'").as(StepVerifier::create).expectNext(true).verifyComplete();

		assertThat(getSession().execute("SELECT * FROM user").one()).isNull();
	}

	@Test // DATACASS-335
	public void queryForObjectShouldReturnFirstColumn() {

		template.queryForObject("SELECT id FROM user;", String.class).as(StepVerifier::create) //
				.expectNext("WHITE") //
				.verifyComplete();
	}

	@Test // DATACASS-335
	public void queryForObjectShouldReturnMap() {

		template.queryForMap("SELECT * FROM user;").as(StepVerifier::create) //
				.consumeNextWith(actual -> {

					assertThat(actual).containsEntry("id", "WHITE").containsEntry("username", "Walter");
				}).verifyComplete();
	}

	@Test // DATACASS-335
	public void executeStatementShouldRemoveRecords() {

		template.execute(SimpleStatement.newInstance("DELETE FROM user WHERE id = 'WHITE'")).as(StepVerifier::create) //
				.expectNext(true) //
				.verifyComplete();

		assertThat(getSession().execute("SELECT * FROM user").one()).isNull();
	}

	@Test // DATACASS-335
	public void queryForObjectStatementShouldReturnFirstColumn() {

		template.queryForObject(SimpleStatement.newInstance("SELECT id FROM user"), String.class).as(StepVerifier::create) //
				.expectNext("WHITE") //
				.verifyComplete();
	}

	@Test // DATACASS-335
	public void queryForObjectStatementShouldReturnMap() {

		template.queryForMap(SimpleStatement.newInstance("SELECT * FROM user")).as(StepVerifier::create) //
				.consumeNextWith(actual -> {

					assertThat(actual).containsEntry("id", "WHITE").containsEntry("username", "Walter");
				}).verifyComplete();
	}

	@Test // DATACASS-335
	public void executeWithArgsShouldRemoveRecords() {

		template.execute("DELETE FROM user WHERE id = ?", "WHITE").as(StepVerifier::create).expectNext(true)
				.verifyComplete();

		assertThat(getSession().execute("SELECT * FROM user").one()).isNull();
	}

	@Test // DATACASS-335
	public void queryForObjectWithArgsShouldReturnFirstColumn() {

		template.queryForObject("SELECT id FROM user WHERE id = ?;", String.class, "WHITE").as(StepVerifier::create) //
				.expectNext("WHITE") //
				.verifyComplete();
	}

	@Test // DATACASS-335
	public void queryForObjectWithArgsShouldReturnMap() {

		template.queryForMap("SELECT * FROM user WHERE id = ?;", "WHITE").as(StepVerifier::create) //
				.consumeNextWith(actual -> {

					assertThat(actual).containsEntry("id", "WHITE").containsEntry("username", "Walter");
				}).verifyComplete();
	}
}
