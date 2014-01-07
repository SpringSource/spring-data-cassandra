/*
 * Copyright 2011-2012 the original author or authors.
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
package org.springframework.data.cassandra.mapping;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.springframework.cassandra.core.Ordering;
import org.springframework.cassandra.core.PrimaryKeyType;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.data.util.TypeInformation;
import org.springframework.util.StringUtils;

import com.datastax.driver.core.DataType;

/**
 * Cassandra specific {@link org.springframework.data.mapping.model.AnnotationBasedPersistentProperty} implementation.
 * 
 * @author Alex Shvid
 */
public class BasicCassandraPersistentProperty extends AnnotationBasedPersistentProperty<CassandraPersistentProperty>
		implements CassandraPersistentProperty {

	/**
	 * Creates a new {@link BasicCassandraPersistentProperty}.
	 * 
	 * @param field
	 * @param propertyDescriptor
	 * @param owner
	 * @param simpleTypeHolder
	 */
	public BasicCassandraPersistentProperty(Field field, PropertyDescriptor propertyDescriptor,
			CassandraPersistentEntity<?> owner, CassandraSimpleTypeHolder simpleTypeHolder) {
		super(field, propertyDescriptor, owner, simpleTypeHolder);
	}

	@Override
	public boolean isIdProperty() {

		if (super.isIdProperty()) {
			return true;
		}

		return isAnnotationPresent(PrimaryKey.class);
	}

	@Override
	public boolean isCompositePrimaryKey() {
		return getField().getType().isAnnotationPresent(CompositePrimaryKey.class);
	}

	@Override
	public Class<?> getCompositePrimaryKeyType() {
		if (!isCompositePrimaryKey()) {
			return null;
		}

		return getField().getType();
	}

	@Override
	public CassandraPersistentEntity<?> getCompositePrimaryKeyEntity() {
		if (!isCompositePrimaryKey()) {
			return null;
		}

		return (CassandraPersistentEntity<?>) ClassTypeInformation.from(getCompositePrimaryKeyType());
	}

	public String getColumnName() {

		// first check @Column annotation
		Column column = findAnnotation(Column.class);
		if (column != null && StringUtils.hasText(column.value())) {
			return column.value();
		}

		// else check @KeyColumn annotation
		PrimaryKeyColumn pk = findAnnotation(PrimaryKeyColumn.class);
		if (pk != null && StringUtils.hasText(pk.value())) {
			return pk.value();
		}

		// else default
		return field.getName().toLowerCase();
	}

	public Ordering getOrdering() {

		PrimaryKeyColumn anno = findAnnotation(PrimaryKeyColumn.class);

		return anno == null ? null : anno.ordering();
	}

	public DataType getDataType() {

		CassandraType annotation = findAnnotation(CassandraType.class);
		if (annotation != null) {
			return getDataTypeFor(annotation);
		}

		if (isMap()) {

			List<TypeInformation<?>> args = getTypeInformation().getTypeArguments();
			ensureTypeArguments(args.size(), 2);

			return DataType.map(getDataTypeFor(args.get(0).getType()), getDataTypeFor(args.get(1).getType()));
		}

		if (isCollectionLike()) {

			List<TypeInformation<?>> args = getTypeInformation().getTypeArguments();
			ensureTypeArguments(args.size(), 1);

			if (Set.class.isAssignableFrom(getType())) {
				return DataType.set(getDataTypeFor(args.get(0).getType()));
			}
			if (List.class.isAssignableFrom(getType())) {
				return DataType.list(getDataTypeFor(args.get(0).getType()));
			}
		}

		DataType dataType = CassandraSimpleTypeHolder.getDataTypeFor(getType());
		if (dataType == null) {
			throw new InvalidDataAccessApiUsageException(
					String
							.format(
									"unknown type for property [%s], type [%s] in entity [%s]; only primitive types and collections or maps of primitive types are allowed",
									getName(), getType(), getOwner().getName()));
		}
		return dataType;
	}

	private DataType getDataTypeFor(CassandraType annotation) {

		DataType.Name type = annotation.type();

		if (type.isCollection()) {
			switch (type) {

			case MAP:
				ensureTypeArguments(annotation.typeArguments().length, 2);
				return DataType.map(getDataTypeFor(annotation.typeArguments()[0]),
						getDataTypeFor(annotation.typeArguments()[1]));

			case LIST:
				ensureTypeArguments(annotation.typeArguments().length, 1);
				return DataType.list(getDataTypeFor(annotation.typeArguments()[0]));

			case SET:
				ensureTypeArguments(annotation.typeArguments().length, 1);
				return DataType.set(getDataTypeFor(annotation.typeArguments()[0]));

			default:
				throw new InvalidDataAccessApiUsageException(
						String.format("unknown multivalued DataType [%s] for property [%s] in entity [%s]", type, getType(),
								getOwner().getName()));
			}
		} else {

			return CassandraSimpleTypeHolder.getDataTypeFor(type);
		}
	}

	public boolean isIndexed() {
		return isAnnotationPresent(Indexed.class);
	}

	public boolean isPartitionKeyColumn() {

		PrimaryKeyColumn anno = findAnnotation(PrimaryKeyColumn.class);

		return anno != null && anno.type() == PrimaryKeyType.PARTITIONED;
	}

	@Override
	public boolean isClusterKeyColumn() {

		PrimaryKeyColumn anno = findAnnotation(PrimaryKeyColumn.class);

		return anno != null && anno.type() == PrimaryKeyType.CLUSTERED;
	}

	@Override
	public boolean isPrimaryKeyColumn() {
		return isAnnotationPresent(PrimaryKeyColumn.class);
	}

	@Override
	protected Association<CassandraPersistentProperty> createAssociation() {
		return new Association<CassandraPersistentProperty>(this, null);
	}

	protected DataType getDataTypeFor(DataType.Name typeName) {
		DataType dataType = CassandraSimpleTypeHolder.getDataTypeFor(typeName);
		if (dataType == null) {
			throw new InvalidDataAccessApiUsageException(
					"only primitive types are allowed inside collections for the property  '" + this.getName() + "' type is '"
							+ this.getType() + "' in the entity " + this.getOwner().getName());
		}
		return dataType;
	}

	protected DataType getDataTypeFor(Class<?> javaType) {
		DataType dataType = CassandraSimpleTypeHolder.getDataTypeFor(javaType);
		if (dataType == null) {
			throw new InvalidDataAccessApiUsageException(
					"only primitive types are allowed inside collections for the property  '" + this.getName() + "' type is '"
							+ this.getType() + "' in the entity " + this.getOwner().getName());
		}
		return dataType;
	}

	protected void ensureTypeArguments(int args, int expected) {
		if (args != expected) {
			throw new InvalidDataAccessApiUsageException("expected " + expected + " of typed arguments for the property  '"
					+ this.getName() + "' type is '" + this.getType() + "' in the entity " + this.getOwner().getName());
		}
	}
}
