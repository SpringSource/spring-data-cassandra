/*
 *  Copyright 2013-2016 the original author or authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.data.cassandra.mapping;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.cassandra.core.cql.CqlIdentifier;

/**
 * The CassandraPersistentPropertyComparatorUnitTests class is a test suite of test cases testing the contract and
 * functionality of the {@link CassandraPersistentPropertyComparator} class.
 *
 * @author John Blum
 * @see org.springframework.data.cassandra.mapping.CassandraPersistentPropertyComparator
 * @since 1.5.0
 */
@RunWith(MockitoJUnitRunner.class)
public class CassandraPersistentPropertyComparatorUnitTests {

	@Mock private CassandraPersistentProperty left;

	@Mock private CassandraPersistentProperty right;

	@Test
	public void leftAndRightAreNullReturnsZero() {
		assertThat(CassandraPersistentPropertyComparator.IT.compare(null, null), is(equalTo(0)));
		verifyZeroInteractions(left);
		verifyZeroInteractions(right);
	}

	@Test
	public void leftIsNotNullAndRightIsNullReturnsOne() {
		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, null), is(equalTo(1)));
		verifyZeroInteractions(left);
		verifyZeroInteractions(right);
	}

	@Test
	public void leftIsNullAndRightIsNotNullReturnsMinusOne() {
		assertThat(CassandraPersistentPropertyComparator.IT.compare(null, right), is(equalTo(-1)));
		verifyZeroInteractions(left);
		verifyZeroInteractions(right);
	}

	@Test
	public void leftAndRightAreEqualReturnsZero() {
		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, left), is(equalTo(0)));
		assertThat(CassandraPersistentPropertyComparator.IT.compare(right, right), is(equalTo(0)));
		verifyZeroInteractions(left);
		verifyZeroInteractions(right);
	}

	@Test
	public void leftAndRightAreCompositePrimaryKeysReturnsZero() {
		when(left.isCompositePrimaryKey()).thenReturn(true);
		when(right.isCompositePrimaryKey()).thenReturn(true);

		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, right), is(equalTo(0)));

		verify(left, times(1)).isCompositePrimaryKey();
		verify(right, times(1)).isCompositePrimaryKey();
	}

	@Test
	public void leftIsCompositePrimaryKeyReturnsMinusOne() {
		when(left.isCompositePrimaryKey()).thenReturn(true);
		when(left.isPrimaryKeyColumn()).thenReturn(false);
		when(right.isCompositePrimaryKey()).thenReturn(false);
		when(right.isPrimaryKeyColumn()).thenReturn(false);

		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, right), is(equalTo(-1)));

		verify(left, times(1)).isCompositePrimaryKey();
		verify(left, times(1)).isPrimaryKeyColumn();
		verify(right, times(1)).isCompositePrimaryKey();
		verify(right, times(1)).isPrimaryKeyColumn();
	}

	@Test
	public void leftIsPrimaryKeyColumnReturnsMinusOne() {
		when(left.isCompositePrimaryKey()).thenReturn(false);
		when(left.isPrimaryKeyColumn()).thenReturn(true);
		when(right.isCompositePrimaryKey()).thenReturn(false);
		when(right.isPrimaryKeyColumn()).thenReturn(false);

		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, right), is(equalTo(-1)));

		verify(left, times(1)).isCompositePrimaryKey();
		verify(left, times(1)).isPrimaryKeyColumn();
		verify(right, times(1)).isCompositePrimaryKey();
		verify(right, times(1)).isPrimaryKeyColumn();
	}

	@Test
	public void rightIsCompositePrimaryKeyReturnsOne() {
		when(left.isCompositePrimaryKey()).thenReturn(false);
		when(left.isPrimaryKeyColumn()).thenReturn(false);
		when(right.isCompositePrimaryKey()).thenReturn(true);
		when(right.isPrimaryKeyColumn()).thenReturn(false);

		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, right), is(equalTo(1)));

		verify(left, times(1)).isCompositePrimaryKey();
		verify(left, times(1)).isPrimaryKeyColumn();
		verify(right, times(1)).isCompositePrimaryKey();
		verify(right, times(1)).isPrimaryKeyColumn();
	}

	@Test
	public void rightIsPrimaryKeyColumnReturnsOne() {
		when(left.isCompositePrimaryKey()).thenReturn(false);
		when(left.isPrimaryKeyColumn()).thenReturn(false);
		when(right.isCompositePrimaryKey()).thenReturn(false);
		when(right.isPrimaryKeyColumn()).thenReturn(true);

		assertThat(CassandraPersistentPropertyComparator.IT.compare(left, right), is(equalTo(1)));

		verify(left, times(1)).isCompositePrimaryKey();
		verify(left, times(1)).isPrimaryKeyColumn();
		verify(right, times(1)).isCompositePrimaryKey();
		verify(right, times(1)).isPrimaryKeyColumn();
	}

	@Test
	public void compareLeftAndRightNamesReturnsNegativeValue() {

		when(left.isCompositePrimaryKey()).thenReturn(false);
		when(left.isPrimaryKeyColumn()).thenReturn(true);
		when(right.isCompositePrimaryKey()).thenReturn(true);
		when(right.isPrimaryKeyColumn()).thenReturn(false);
		when(left.getColumnName()).thenReturn(CqlIdentifier.cqlId("left"));
		when(right.getColumnName()).thenReturn(CqlIdentifier.cqlId("right"));

		assertThat(CassandraPersistentPropertyComparator.INSTANCE.compare(left, right), is(lessThan(0)));

		verify(left, times(1)).isCompositePrimaryKey();
		verify(left, times(1)).isPrimaryKeyColumn();
		verify(left, times(1)).getColumnName();
		verify(right, times(1)).isCompositePrimaryKey();
		verify(right, times(1)).isPrimaryKeyColumn();
		verify(right, times(1)).getColumnName();
	}

	/**
	 * @see DATACASS-352
	 */
	@Test
	public void columnNameComparisonShouldHonorContract() throws Exception {

		BasicCassandraMappingContext context = new BasicCassandraMappingContext();
		CassandraPersistentEntity<?> persistentEntity = context.getPersistentEntity(TwoColumns.class);

		CassandraPersistentProperty annotated = persistentEntity.getPersistentProperty("annotated");
		CassandraPersistentProperty another = persistentEntity.getPersistentProperty("anotherAnnotated");
		CassandraPersistentProperty plain = persistentEntity.getPersistentProperty("plain");

		assertThat(CassandraPersistentPropertyComparator.IT.compare(annotated, plain), is(lessThanOrEqualTo(-1)));
		assertThat(CassandraPersistentPropertyComparator.IT.compare(plain, annotated), is(greaterThanOrEqualTo(1)));
		assertThat(CassandraPersistentPropertyComparator.IT.compare(another, another), is(0));
	}

	static class TwoColumns {

		@Column("annotated") String annotated;

		@Column("another") String anotherAnnotated;

		String plain;
	}
}
