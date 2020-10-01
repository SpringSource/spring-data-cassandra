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
package org.springframework.data.cassandra.core;

import java.util.Optional;

import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.cassandra.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import com.datastax.oss.driver.api.core.CqlIdentifier;

/**
 * Implementation of {@link ExecutableUpdateOperation}.
 *
 * @author Mark Paluch
 * @author Tomasz Lelek
 * @see org.springframework.data.cassandra.core.ExecutableUpdateOperation
 * @see org.springframework.data.cassandra.core.query.Query
 * @see org.springframework.data.cassandra.core.query.Update
 * @since 2.1
 */
class ExecutableUpdateOperationSupport implements ExecutableUpdateOperation {

	private final CassandraTemplate template;

	public ExecutableUpdateOperationSupport(CassandraTemplate template) {
		this.template = template;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.cassandra.core.ExecutableUpdateOperation#update(java.lang.Class)
	 */
	@Override
	public ExecutableUpdate update(Class<?> domainType) {

		Assert.notNull(domainType, "DomainType must not be null");

		return new ExecutableUpdateSupport(this.template, domainType, Query.empty(), null, null);
	}

	static class ExecutableUpdateSupport implements ExecutableUpdate, TerminatingUpdate {

		private final CassandraTemplate template;

		private final Class<?> domainType;

		private final Query query;

		private final @Nullable CqlIdentifier keyspaceName;
		private final @Nullable CqlIdentifier tableName;

		public ExecutableUpdateSupport(CassandraTemplate template, Class<?> domainType, Query query,
				@Nullable CqlIdentifier keyspaceName, @Nullable CqlIdentifier tableName) {
			this.template = template;
			this.domainType = domainType;
			this.query = query;
			this.keyspaceName = keyspaceName;
			this.tableName = tableName;
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.ExecutableUpdateOperation.UpdateWithTable#inTable(com.datastax.oss.driver.api.core.CqlIdentifier)
		 */
		@Override
		public UpdateWithQuery inTable(CqlIdentifier tableName) {

			Assert.notNull(tableName, "Table name must not be null");

			return new ExecutableUpdateSupport(this.template, this.domainType, this.query, null, tableName);
		}
		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.ExecutableUpdateOperation.UpdateWithTable#inTable(com.datastax.oss.driver.api.core.CqlIdentifier, com.datastax.oss.driver.api.core.CqlIdentifier)
		 */
		@Override
		public UpdateWithQuery inTable(CqlIdentifier keyspaceName, CqlIdentifier tableName) {
			Assert.notNull(tableName, "Table name must not be null");
			return new ExecutableUpdateSupport(this.template, this.domainType, this.query, keyspaceName, tableName);
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.ExecutableUpdateOperation.UpdateWithQuery#matching(org.springframework.data.cassandra.core.query.Query)
		 */
		@Override
		public TerminatingUpdate matching(Query query) {

			Assert.notNull(query, "Query must not be null");

			return new ExecutableUpdateSupport(this.template, this.domainType, query, this.keyspaceName, this.tableName);
		}

		/* (non-Javadoc)
		 * @see org.springframework.data.cassandra.core.ExecutableUpdateOperation.TerminatingUpdate#apply(org.springframework.data.cassandra.core.query.Update)
		 */
		@Override
		public WriteResult apply(Update update) {

			Assert.notNull(update, "Update must not be null");

			return this.template.doUpdate(this.query, update, this.domainType, getTableCoordinates());
		}

		private Optional<CqlIdentifier> getKeyspaceName() {
			return this.keyspaceName != null ? Optional.of(this.keyspaceName)
					: this.template.getKeyspaceName(this.domainType);
		}

		private CqlIdentifier getTableName() {
			return this.tableName != null ? this.tableName : this.template.getTableName(this.domainType);
		}

		private TableCoordinates getTableCoordinates() {
			return TableCoordinates.of(getKeyspaceName(), getTableName());
		}
	}
}
