package org.springframework.cassandra.test.integration.config.xml;

import static org.junit.Assert.*;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.cassandra.core.CassandraOperations;
import org.springframework.cassandra.test.integration.AbstractEmbeddedCassandraIntegrationTest;
import org.springframework.cassandra.test.integration.config.IntegrationTestUtils;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.datastax.driver.core.Session;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class MinimalXmlConfigTest extends AbstractEmbeddedCassandraIntegrationTest {

	protected String keyspace() {
		return "minimalxmlconfigtest";
	}

	@Inject
	Session s;

	@Inject
	CassandraOperations ops;

	@Test
	public void test() {
		IntegrationTestUtils.assertSession(s);
		IntegrationTestUtils.assertKeyspaceExists(keyspace(), s);

		assertNotNull(ops);
	}
}
