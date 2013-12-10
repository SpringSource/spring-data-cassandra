# Spring Data Cassandra Project Info

## Release Preview

The goal of this release preview is to publish the pieces of spring-data-cassandra as they become available
so that user's of the module can start to familiarize themselves with the components, and ultimately to provide
the development team feedback.  We hope this iterative approach produces the most usable and developer friendly
``spring-data-cassandra`` repository.

### What's included (Q4 - 2013)

There are two modules included in the ``spring-data-cassandra`` repository:  ``spring-cassandra`` and ``spring-data-cassandra``.

#### Module ``spring-cassandra``

This is the low-level core template framework, like the ones you are used to using on all your Spring projects.  Our
``CassandraTemplate`` provides everything you need for working with Cassandra using Spring's familiar template pattern.

This includes persistence exception translation, Spring JavaConfig and XML configuration support.  Define your Spring beans to setup your
Cassandra ``Cluster`` object, then create your ``Session`` and you are ready to interact with Cassandra using the ``CassandraTemplate``.

The module also offers table operation builders for ``CREATE TABLE``, ``ALTER TABLE``, and ``DROP TABLE`` operations.

#### Module ``spring-data-cassandra``

The ``spring-data-cassandra`` module depends on the ``spring-cassandra`` module and adds the familiar Spring Data features like repositories and lightweight POJO persistence.  _The code in the ``spring-data-cassandra`` module is a work in progress and is not yet functional._  We are actively working on its completion, but wanted to make available to the Spring and Cassandra communities the lower level Cassandra template functionality.

#### Best practices

We have worked closely with the DataStax Driver Engineering team to ensure that our implementation around their native
CQL Driver takes advantage of all that it has to offer. If you need access to more than one keyspace in your application,
create more than one ``CassandraTemplate`` (one per session, one session per keyspace), then use the appropriate template instance where needed.

Here are some considerations when designing your application for use with ``spring-cassandra``.

* When creating a template, wire in a single ``Session`` per keyspace.  _Use one template per keyspace!_
* Cassandra's ``Session`` object is thread-safe, so you only need one per entire application.
* Do not issue ``USE <keyspace>`` commands on your session.
* The DataStax Java Driver handles all failover and retry logic for you.  Become familiar with the [Driver Documentation](http://www.datastax.com/documentation/developer/java-driver/1.0/webhelp/index.html), which will help you configure your ``Cluster``.
* If you are using a Cassandra ``Cluster`` spanning multiple data centers, please be insure to include hosts from both data centers in your contact points.

#### High Performance Ingestion

We have included a variety of overloaded ``ingest()`` methods in the template for high performance batch writes.

### What's Next (Q1 - 2014):  Spring _Data_ Cassandra

The next round of work to do is to complete module ``spring-data-cassandra``, while taking feedback from the community's use of module ``spring-cassandra``.

#### Cassandra Admin Template

This is another Spring template class to help you with all of your keyspace and table administration tasks.

#### Cassandra Data Template

This template extends ``CassandraTemplate`` to provide even more interaction with Cassandra using annotated POJOs.
The Spring Data Cassandra Repository implementation is a client of ``CassandraDataTemplate``.  This _data_ template gives the developer the capability of working with annotated POJOs and the template pattern without the requirement of the Spring Data ``Repository`` interface.

#### Cassandra Repository

The implementation of the standard Spring Data Repository interface for Cassandra.

#### Official Reference Guide

Once we have all the inner workings of the Repository interface completed, we will publish a full Reference Guide on using all of the features in spring-data-cassandra.

## Examples

### JavaConfig

Here is a very basic example to get your project connected to Cassandra 1.2 running on your local machine.

	@Configuration
	public class TestConfig extends AbstractCassandraConfiguration {

		public static final String keyspace = "test";

		@Override
		protected String getKeyspaceName() {
			return keyspace;
		}

		@Override
		@Bean
		public Cluster cluster() {

			Builder builder = Cluster.builder();
			builder.addContactPoint("127.0.0.1");
			return builder.build();
		}

		@Bean
		public SessionFactoryBean sessionFactoryBean() {

			SessionFactoryBean bean = new SessionFactoryBean(cluster(), getKeyspaceName());
			return bean;

		}

		@Bean
		public CassandraOperations cassandraTemplate() {

			CassandraOperations template = new CassandraTemplate(sessionFactoryBean().getObject());
			return template;
		}

	}

### XML Configuration

	<cassandra-cluster />
	<cassandra-session keyspace-name="test" />
	<cassandra-template />

### Using the Template

	public class CassandraDataOperationsTest {

		@Autowired
		private CassandraOperations template;
		
		public Integer getCount() throws DataAccessException {
		
			String cql = "select count(*) from table_name";
			
			Integer count = template.queryForObject(cql, Integer.class);
			
			log.info("Row Count is -> " + count);
			
			return count;
			
		}
	}

## Current Status

This community-led [Spring Data](http://projects.spring.io/spring-data)
subproject is well underway.

A first milestone (M1) is expected sometime in early 1Q14 built on the
following artifacts (or more recent versions thereof):

* Spring Data Commons 1.7.x
* Cassandra 1.2
* Datastax Java Driver 1.x
* JDK 1.6+

The GA release is expected as part of the as-yet unnamed fourth Spring
Data Release Train "D", following Spring Data Release Train
[Codd](https://github.com/spring-projects/spring-data-commons/wiki/Release-Train-Codd).


## Cassandra 2.x

We are anticipating support for Cassandra 2.x and Datastax Java Driver
2.x in a parallel branch after the 1.x-based support has been
released.


## Source Repository & Issue Tracking

Source for this module is hosted on GitHub in [Spring Projects](https://github.com/spring-projects).  
The Spring Data Cassandra JIRA can be found [here](https://jira.springsource.org/browse/DATACASS).

## Reporting Problems

If you encounter a problem using this module, please report the issue to us using [JIRA](https://jira.springsource.org/browse/DATACASS).  Please provide as much information as you can, including:

* Cassandra version
* Community or DataStax distribution of Cassandra
* JDK Version
* Output of ``show schema`` where applicable
* Any stacktraces showing the location of the problem
* Steps to reproduce
* Any other relevant information that you would like to see if you were diagnosing the problem.

Please do not post anything to Jira or the mailing list on how to use Cassandra.  That is beyond the scope
of this module and there are a variety of resources available targeting that specific subject.

We recommend reading the following:

* [PlanetCassandra.org](http://planetcassandra.org/)
* [Getting Started](http://wiki.apache.org/cassandra/GettingStarted)
* [Driver Documentation](http://www.datastax.com/documentation/developer/java-driver/1.0/webhelp/index.html)


## Contact

For more information, feel free to contact the individuals listed
below:

* David Webb:  dwebb _at_ prowaveconsulting _dot_ com
* Matthew Adams:  matthew _dot_ adams _at_ scispike _dot_ com

Also, developer discussions are being hosted via Google Groups at https://groups.google.com/forum/#!forum/spring-data-cassandra.

## Sponsoring Companies

Spring Data Cassandra is being led and supported by the following
companies and individuals:

* [Prowave Consulting](http://www.prowaveconsulting.com)
* [SciSpike](http://www.scispike.com)
* [VHA](http://www.vha.com)
* Alexander Shvid

The following companies and individuals are also generously providing
support:

* [DataStax](http://www.datastax.com)
* [Spring](http://www.spring.io) @ [Pivotal](http://www.gopivotal.com)
