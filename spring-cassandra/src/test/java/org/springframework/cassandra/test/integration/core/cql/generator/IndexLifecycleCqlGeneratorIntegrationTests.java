package org.springframework.cassandra.test.integration.core.cql.generator;

import static org.springframework.cassandra.test.integration.core.cql.generator.CqlIndexSpecificationAssertions.assertIndex;
import static org.springframework.cassandra.test.integration.core.cql.generator.CqlIndexSpecificationAssertions.assertNoIndex;

import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cassandra.test.integration.AbstractEmbeddedCassandraIntegrationTest;
import org.springframework.cassandra.test.unit.core.cql.generator.CreateIndexCqlGeneratorTests;
import org.springframework.cassandra.test.unit.core.cql.generator.DropIndexCqlGeneratorTests;

/**
 * Integration tests that reuse unit tests.
 * 
 * @author Matthew T. Adams
 */
public class IndexLifecycleCqlGeneratorIntegrationTests extends AbstractEmbeddedCassandraIntegrationTest {

	Logger log = LoggerFactory.getLogger(IndexLifecycleCqlGeneratorIntegrationTests.class);

	/**
	 * This loads any test specific Cassandra objects
	 */
	@Rule
	public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet(
			"integration/cql/generator/CreateIndexCqlGeneratorIntegrationTests-BasicTest.cql", this.keyspace),
			CASSANDRA_CONFIG, CASSANDRA_HOST, CASSANDRA_NATIVE_PORT);

	@Test
	public void lifecycleTest() {

		CreateIndexCqlGeneratorTests.BasicTest createTest = new CreateIndexCqlGeneratorTests.BasicTest();
		DropIndexCqlGeneratorTests.BasicTest dropTest = new DropIndexCqlGeneratorTests.BasicTest();
		DropIndexCqlGeneratorTests.IfExistsTest dropIfExists = new DropIndexCqlGeneratorTests.IfExistsTest();

		createTest.prepare();
		dropTest.prepare();
		dropIfExists.prepare();

		log.info(createTest.cql);
		session.execute(createTest.cql);

		assertIndex(createTest.specification, keyspace, session);

		log.info(dropTest.cql);
		session.execute(dropTest.cql);

		assertNoIndex(createTest.specification, keyspace, session);

		// log.info(dropIfExists.cql);
		// session.execute(dropIfExists.cql);
		//
		// assertNoIndex(createTest.specification, keyspace, session);

	}

}