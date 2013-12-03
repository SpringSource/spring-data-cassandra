/*
 * Copyright 2011-2013 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cassandra.core.keyspace;

import static org.springframework.cassandra.core.cql.CqlStringUtils.checkIdentifier;
import static org.springframework.cassandra.core.cql.CqlStringUtils.identifize;
import static org.springframework.cassandra.core.cql.CqlStringUtils.noNull;
import static org.springframework.cassandra.core.PrimaryKeyType.PARTITIONED;
import static org.springframework.cassandra.core.PrimaryKeyType.CLUSTERED;
import static org.springframework.cassandra.core.Ordering.ASCENDING;

import org.springframework.cassandra.core.PrimaryKeyType;
import org.springframework.cassandra.core.Ordering;

import com.datastax.driver.core.DataType;

/**
 * Builder class to help construct CQL statements that involve column manipulation. Not threadsafe.
 * <p/>
 * Use {@link #name(String)} and {@link #type(String)} to set the name and type of the column, respectively. To specify
 * a <code>PRIMARY KEY</code> column, use {@link #primary()} or {@link #primary(Ordering)}. To specify that the
 * <code>PRIMARY KEY</code> column is or is part of the partition key, use {@link #partition()} instead of
 * {@link #primary()} or {@link #primary(Ordering)}.
 * 
 * @author Matthew T. Adams
 * @author Alex Shvid
 */
public class ColumnSpecification {

	/**
	 * Default ordering of primary key fields; value is {@link Ordering#ASCENDING}.
	 */
	public static final Ordering DEFAULT_ORDERING = ASCENDING;

	private String name;
	private DataType type; // TODO: determining if we should be coupling this to Datastax Java Driver type?
	private PrimaryKeyType keyType;
	private Ordering ordering;

	/**
	 * Sets the column's name.
	 * 
	 * @return this
	 */
	public ColumnSpecification name(String name) {
		checkIdentifier(name);
		this.name = name;
		return this;
	}

	/**
	 * Sets the column's type.
	 * 
	 * @return this
	 */
	public ColumnSpecification type(DataType type) {
		this.type = type;
		return this;
	}

	/**
	 * Identifies this column as a primary key column that is also part of a partition key. Sets the column's
	 * {@link #keyType} to {@link PrimaryKeyType#PARTITIONED} and its {@link #ordering} to <code>null</code>.
	 * 
	 * @return this
	 */
	public ColumnSpecification partition() {
		return partition(true);
	}

	/**
	 * Toggles the identification of this column as a primary key column that also is or is part of a partition key. Sets
	 * {@link #ordering} to <code>null</code> and, if the given boolean is <code>true</code>, then sets the column's
	 * {@link #keyType} to {@link PrimaryKeyType#PARTITIONED}, else sets it to <code>null</code>.
	 * 
	 * @return this
	 */
	public ColumnSpecification partition(boolean partition) {
		this.keyType = partition ? PARTITIONED : null;
		this.ordering = null;
		return this;
	}

	/**
	 * Identifies this column as a primary key column with default ordering. Sets the column's {@link #keyType} to
	 * {@link PrimaryKeyType#CLUSTERED} and its {@link #ordering} to {@link #DEFAULT_ORDERING}.
	 * 
	 * @return this
	 */
	public ColumnSpecification primary() {
		return primary(DEFAULT_ORDERING);
	}

	/**
	 * Identifies this column as a primary key column with the given ordering. Sets the column's {@link #keyType} to
	 * {@link PrimaryKeyType#CLUSTERED} and its {@link #ordering} to the given {@link Ordering}.
	 * 
	 * @return this
	 */
	public ColumnSpecification primary(Ordering order) {
		return primary(order, true);
	}

	/**
	 * Toggles the identification of this column as a primary key column. If the given boolean is <code>true</code>, then
	 * sets the column's {@link #keyType} to {@link PrimaryKeyType#PARTITIONED} and {@link #ordering} to the given
	 * {@link Ordering} , else sets both {@link #keyType} and {@link #ordering} to <code>null</code>.
	 * 
	 * @return this
	 */
	public ColumnSpecification primary(Ordering order, boolean primary) {
		this.keyType = primary ? CLUSTERED : null;
		this.ordering = primary ? order : null;
		return this;
	}

	/**
	 * Sets the column's {@link #keyType}.
	 * 
	 * @return this
	 */
	/* package */ColumnSpecification keyType(PrimaryKeyType keyType) {
		this.keyType = keyType;
		return this;
	}

	/**
	 * Sets the column's {@link #ordering}.
	 * 
	 * @return this
	 */
	/* package */ColumnSpecification ordering(Ordering ordering) {
		this.ordering = ordering;
		return this;
	}

	public String getName() {
		return name;
	}

	public String getNameAsIdentifier() {
		return identifize(name);
	}

	public DataType getType() {
		return type;
	}

	public PrimaryKeyType getKeyType() {
		return keyType;
	}

	public Ordering getOrdering() {
		return ordering;
	}

	public String toCql() {
		return toCql(null).toString();
	}

	public StringBuilder toCql(StringBuilder cql) {
		return (cql = noNull(cql)).append(name).append(" ").append(type);
	}

	@Override
	public String toString() {
		return toCql(null).append(" /* keyType=").append(keyType).append(", ordering=").append(ordering).append(" */ ")
				.toString();
	}
}