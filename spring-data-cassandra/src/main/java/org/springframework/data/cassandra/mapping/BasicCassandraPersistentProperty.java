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
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.SimpleTypeHolder;
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
			CassandraPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {
		super(field, propertyDescriptor, owner, simpleTypeHolder);
	}

	/**
	 * Also considers fields that has an Id annotation.
	 * 
	 */
	@Override
	public boolean isIdProperty() {

		if (super.isIdProperty()) {
			return true;
		}

		return getField().isAnnotationPresent(Id.class);
	}

	/**
	 * Returns the true if the field composite primary key.
	 * 
	 * @return
	 */
	@Override
	public boolean isCompositePrimaryKey() {
		Class<?> fieldType = getField().getType();
		return fieldType.isAnnotationPresent(CompositePrimaryKey.class);
	}

	/**
	 * Returns the column name to be used to store the value of the property inside the Cassandra.
	 * 
	 * @return
	 */
	public String getColumnName() {
		Column annotation = getField().getAnnotation(Column.class);
		return annotation != null && StringUtils.hasText(annotation.value()) ? annotation.value() : field.getName();
	}

	/**
	 * Returns ordering for the column. Valid only for clustered columns.
	 * 
	 * @return
	 */
	public Ordering getOrdering() {
		Order annotation = getField().getAnnotation(Order.class);
		return annotation != null ? annotation.value() : null;
	}

	/**
	 * Returns the data type information if exists.
	 * 
	 * @return
	 */
	public DataType getDataType() {
		Qualify annotation = getField().getAnnotation(Qualify.class);
		if (annotation != null && annotation.type() != null) {
			return qualifyAnnotatedType(annotation);
		}
		if (isMap()) {
			List<TypeInformation<?>> args = getTypeInformation().getTypeArguments();
			ensureTypeArguments(args.size(), 2);
			return DataType.map(autodetectPrimitiveType(args.get(0).getType()),
					autodetectPrimitiveType(args.get(1).getType()));
		}
		if (isCollectionLike()) {
			List<TypeInformation<?>> args = getTypeInformation().getTypeArguments();
			ensureTypeArguments(args.size(), 1);
			if (Set.class.isAssignableFrom(getType())) {
				return DataType.set(autodetectPrimitiveType(args.get(0).getType()));
			} else if (List.class.isAssignableFrom(getType())) {
				return DataType.list(autodetectPrimitiveType(args.get(0).getType()));
			}
		}
		DataType dataType = CassandraSimpleTypes.autodetectPrimitive(this.getType());
		if (dataType == null) {
			throw new InvalidDataAccessApiUsageException(
					"only primitive types and Set,List,Map collections are allowed, unknown type for property '" + this.getName()
							+ "' type is '" + this.getType() + "' in the entity " + this.getOwner().getName());
		}
		return dataType;
	}

	private DataType qualifyAnnotatedType(Qualify annotation) {
		DataType.Name type = annotation.type();
		if (type.isCollection()) {
			switch (type) {
			case MAP:
				ensureTypeArguments(annotation.typeArguments().length, 2);
				return DataType.map(resolvePrimitiveType(annotation.typeArguments()[0]),
						resolvePrimitiveType(annotation.typeArguments()[1]));
			case LIST:
				ensureTypeArguments(annotation.typeArguments().length, 1);
				return DataType.list(resolvePrimitiveType(annotation.typeArguments()[0]));
			case SET:
				ensureTypeArguments(annotation.typeArguments().length, 1);
				return DataType.set(resolvePrimitiveType(annotation.typeArguments()[0]));
			default:
				throw new InvalidDataAccessApiUsageException("unknown collection DataType for property '" + this.getName()
						+ "' type is '" + this.getType() + "' in the entity " + this.getOwner().getName());
			}
		} else {
			return CassandraSimpleTypes.resolvePrimitive(type);
		}
	}

	/**
	 * Returns true if the property has secondary index on this column.
	 * 
	 * @return
	 */
	public boolean isIndexed() {
		return getField().isAnnotationPresent(Indexed.class);
	}

	/**
	 * Returns true if the property has Partitioned annotation on this column.
	 * 
	 * @return
	 */
	public boolean isPartitioned() {
		return getField().isAnnotationPresent(Partitioned.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.mapping.model.AbstractPersistentProperty#createAssociation()
	 */
	@Override
	protected Association<CassandraPersistentProperty> createAssociation() {
		return new Association<CassandraPersistentProperty>(this, null);
	}

	DataType resolvePrimitiveType(DataType.Name typeName) {
		DataType dataType = CassandraSimpleTypes.resolvePrimitive(typeName);
		if (dataType == null) {
			throw new InvalidDataAccessApiUsageException(
					"only primitive types are allowed inside collections for the property  '" + this.getName() + "' type is '"
							+ this.getType() + "' in the entity " + this.getOwner().getName());
		}
		return dataType;
	}

	DataType autodetectPrimitiveType(Class<?> javaType) {
		DataType dataType = CassandraSimpleTypes.autodetectPrimitive(javaType);
		if (dataType == null) {
			throw new InvalidDataAccessApiUsageException(
					"only primitive types are allowed inside collections for the property  '" + this.getName() + "' type is '"
							+ this.getType() + "' in the entity " + this.getOwner().getName());
		}
		return dataType;
	}

	void ensureTypeArguments(int args, int expected) {
		if (args != expected) {
			throw new InvalidDataAccessApiUsageException("expected " + expected + " of typed arguments for the property  '"
					+ this.getName() + "' type is '" + this.getType() + "' in the entity " + this.getOwner().getName());
		}
	}

}
