/*
 * Copyright 2017-2020 the original author or authors.
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
package org.springframework.data.cassandra.core.mapping;

import static org.springframework.data.cassandra.core.cql.keyspace.CreateTableSpecification.*;
import static org.springframework.data.cassandra.core.mapping.CassandraType.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.cassandra.core.convert.CassandraCustomConversions;
import org.springframework.data.cassandra.core.cql.keyspace.CreateIndexSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.CreateTableSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.CreateUserTypeSpecification;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.Optionals;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.detach.AttachmentPoint;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;

/**
 * Default implementation of a {@link MappingContext} for Cassandra using {@link CassandraPersistentEntity} and
 * {@link CassandraPersistentProperty} as primary abstractions.
 *
 * @author Alex Shvid
 * @author Matthew T. Adams
 * @author Mark Paluch
 * @author John Blum
 * @author Jens Schauder
 * @author Vagif Zeynalov
 */
public class CassandraMappingContext
		extends AbstractMappingContext<BasicCassandraPersistentEntity<?>, CassandraPersistentProperty>
		implements ApplicationContextAware, BeanClassLoaderAware {

	private @Nullable ApplicationContext applicationContext;

	private CassandraPersistentEntityMetadataVerifier verifier = new CompositeCassandraPersistentEntityMetadataVerifier();

	private @Nullable ClassLoader beanClassLoader;

	private CustomConversions customConversions = new CassandraCustomConversions(Collections.emptyList());

	private Mapping mapping = new Mapping();

	private TupleTypeFactory tupleTypeFactory = SimpleTupleTypeFactory.DEFAULT;

	private @Nullable UserTypeResolver userTypeResolver;

	private CodecRegistry codecRegistry = CodecRegistry.DEFAULT;

	// caches
	private final Map<CqlIdentifier, Set<CassandraPersistentEntity<?>>> entitySetsByTableName = new HashMap<>();

	private final Set<BasicCassandraPersistentEntity<?>> tableEntities = new HashSet<>();
	private final Set<BasicCassandraPersistentEntity<?>> userDefinedTypes = new HashSet<>();

	/**
	 * Create a new {@link CassandraMappingContext}.
	 */
	public CassandraMappingContext() {
		setSimpleTypeHolder(CassandraSimpleTypeHolder.HOLDER);
	}

	/**
	 * Create a new {@link CassandraMappingContext} given {@link UserTypeResolver} and {@link TupleTypeFactory}.
	 *
	 * @param userTypeResolver must not be {@literal null}.
	 * @param tupleTypeFactory must not be {@literal null}.
	 * @since 2.1
	 */
	public CassandraMappingContext(UserTypeResolver userTypeResolver, TupleTypeFactory tupleTypeFactory) {

		setUserTypeResolver(userTypeResolver);
		setTupleTypeFactory(tupleTypeFactory);
		setSimpleTypeHolder(CassandraSimpleTypeHolder.HOLDER);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mapping.context.AbstractMappingContext#initialize()
	 */
	@Override
	public void initialize() {

		super.initialize();

		processMappingOverrides();
	}

	@SuppressWarnings("all")
	private void processMappingOverrides() {

		this.mapping.getEntityMappings().stream().filter(Objects::nonNull).forEach(entityMapping -> {

			Class<?> entityClass = getEntityClass(entityMapping.getEntityClassName());

			CassandraPersistentEntity<?> entity = getRequiredPersistentEntity(entityClass);

			String entityTableName = entityMapping.getTableName();

			if (StringUtils.hasText(entityTableName)) {
				entity.setTableName(IdentifierFactory.create(entityTableName, Boolean.valueOf(entityMapping.getForceQuote())));
			}

			processMappingOverrides(entity, entityMapping);
		});
	}

	private Class<?> getEntityClass(String entityClassName) {

		try {
			return ClassUtils.forName(entityClassName, this.beanClassLoader);
		} catch (ClassNotFoundException cause) {
			throw new IllegalStateException(String.format("Unknown persistent entity type name [%s]", entityClassName),
					cause);
		}
	}

	private static void processMappingOverrides(CassandraPersistentEntity<?> entity, EntityMapping entityMapping) {

		entityMapping.getPropertyMappings()
				.forEach((key, propertyMapping) -> processMappingOverride(entity, propertyMapping));
	}

	private static void processMappingOverride(CassandraPersistentEntity<?> entity, PropertyMapping mapping) {

		CassandraPersistentProperty property = entity.getRequiredPersistentProperty(mapping.getPropertyName());

		boolean forceQuote = Boolean.parseBoolean(mapping.getForceQuote());

		property.setForceQuote(forceQuote);

		if (StringUtils.hasText(mapping.getColumnName())) {
			property.setColumnName(IdentifierFactory.create(mapping.getColumnName(), forceQuote));
		}
	}

	/* (non-Javadoc)
	 * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/* (non-Javadoc)
	 * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
	 */
	public void setBeanClassLoader(ClassLoader beanClassLoader) {
		this.beanClassLoader = beanClassLoader;
	}

	/**
	 * Sets the {@link CustomConversions}.
	 *
	 * @param customConversions must not be {@literal null}.
	 * @since 1.5
	 */
	public void setCustomConversions(CustomConversions customConversions) {

		Assert.notNull(customConversions, "CustomConversions must not be null");

		this.customConversions = customConversions;
	}

	/**
	 * Sets the {@link Mapping}.
	 *
	 * @param mapping must not be {@literal null}.
	 */
	public void setMapping(Mapping mapping) {

		Assert.notNull(mapping, "Mapping must not be null");

		this.mapping = mapping;
	}

	/**
	 * Returns only {@link Table} entities.
	 *
	 * @since 1.5
	 */
	public Collection<BasicCassandraPersistentEntity<?>> getTableEntities() {
		return Collections.unmodifiableCollection(this.tableEntities);
	}

	/**
	 * Returns only those entities representing a user defined type.
	 *
	 * @since 1.5
	 */
	public Collection<CassandraPersistentEntity<?>> getUserDefinedTypeEntities() {
		return Collections.unmodifiableSet(this.userDefinedTypes);
	}

	/**
	 * Sets the {@link CodecRegistry}.
	 *
	 * @param codecRegistry must not be {@literal null}.
	 * @since 2.2
	 */
	public void setCodecRegistry(CodecRegistry codecRegistry) {

		Assert.notNull(codecRegistry, "CodecRegistry must not be null");

		this.codecRegistry = codecRegistry;
	}

	@NonNull
	public CodecRegistry getCodecRegistry() {
		return this.codecRegistry;
	}

	/**
	 * Sets the {@link TupleTypeFactory}.
	 *
	 * @param tupleTypeFactory must not be {@literal null}.
	 * @since 2.1
	 */
	public void setTupleTypeFactory(TupleTypeFactory tupleTypeFactory) {

		Assert.notNull(tupleTypeFactory, "TupleTypeFactory must not be null");

		this.tupleTypeFactory = tupleTypeFactory;
	}

	@NonNull
	protected TupleTypeFactory getTupleTypeFactory() {
		return this.tupleTypeFactory;
	}

	/**
	 * Sets the {@link UserTypeResolver}.
	 *
	 * @param userTypeResolver must not be {@literal null}.
	 * @since 1.5
	 */
	public void setUserTypeResolver(UserTypeResolver userTypeResolver) {

		Assert.notNull(userTypeResolver, "UserTypeResolver must not be null");

		this.userTypeResolver = userTypeResolver;
	}

	@Nullable
	protected UserTypeResolver getUserTypeResolver() {
		return this.userTypeResolver;
	}

	/**
	 * @param verifier The verifier to set.
	 */
	public void setVerifier(CassandraPersistentEntityMetadataVerifier verifier) {
		this.verifier = verifier;
	}

	/**
	 * @return Returns the verifier.
	 */
	public CassandraPersistentEntityMetadataVerifier getVerifier() {
		return this.verifier;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mapping.context.AbstractMappingContext#addPersistentEntity(org.springframework.data.util.TypeInformation)
	 */
	@Override
	protected Optional<BasicCassandraPersistentEntity<?>> addPersistentEntity(TypeInformation<?> typeInformation) {

		// Prevent conversion types created as CassandraPersistentEntity
		Optional<BasicCassandraPersistentEntity<?>> optional = shouldCreatePersistentEntityFor(typeInformation)
				? super.addPersistentEntity(typeInformation)
				: Optional.empty();

		optional.ifPresent(entity -> {

			if (entity.isUserDefinedType()) {
				this.userDefinedTypes.add(entity);
			}
			// now do some caching of the entity

			Set<CassandraPersistentEntity<?>> entities = this.entitySetsByTableName.computeIfAbsent(entity.getTableName(),
					cqlIdentifier -> new HashSet<>());

			entities.add(entity);

			if (!entity.isUserDefinedType() && !entity.isTupleType() && entity.isAnnotationPresent(Table.class)) {
				this.tableEntities.add(entity);
			}

		});

		return optional;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mapping.context.AbstractMappingContext#shouldCreatePersistentEntityFor(org.springframework.data.util.TypeInformation)
	 */
	@Override
	protected boolean shouldCreatePersistentEntityFor(TypeInformation<?> typeInfo) {
		return !this.customConversions.hasCustomWriteTarget(typeInfo.getType())
				&& super.shouldCreatePersistentEntityFor(typeInfo);
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mapping.context.AbstractMappingContext#createPersistentEntity(org.springframework.data.util.TypeInformation)
	 */
	@Override
	protected <T> BasicCassandraPersistentEntity<T> createPersistentEntity(TypeInformation<T> typeInformation) {

		BasicCassandraPersistentEntity<T> entity = isUserDefinedType(typeInformation)
				? new CassandraUserTypePersistentEntity<>(typeInformation, getVerifier(), resolveUserTypeResolver())
				: isTuple(typeInformation) ? new BasicCassandraPersistentTupleEntity<>(typeInformation, getTupleTypeFactory())
						: new BasicCassandraPersistentEntity<>(typeInformation, getVerifier());

		Optional.ofNullable(this.applicationContext).ifPresent(entity::setApplicationContext);

		return entity;
	}

	private boolean isTuple(TypeInformation<?> typeInformation) {
		return AnnotatedElementUtils.hasAnnotation(typeInformation.getType(), Tuple.class);
	}

	private boolean isUserDefinedType(TypeInformation<?> typeInformation) {
		return AnnotatedElementUtils.hasAnnotation(typeInformation.getType(), UserDefinedType.class);
	}

	@NonNull
	private UserTypeResolver resolveUserTypeResolver() {

		UserTypeResolver resolvedUserTypeResolver = getUserTypeResolver();

		Assert.state(resolvedUserTypeResolver != null, "UserTypeResolver must not be null");

		return resolvedUserTypeResolver;
	}

	/* (non-Javadoc)
	 * @see org.springframework.data.mapping.context.AbstractMappingContext#createPersistentProperty(org.springframework.data.mapping.model.Property, org.springframework.data.mapping.model.MutablePersistentEntity, org.springframework.data.mapping.model.SimpleTypeHolder)
	 */
	@Override
	protected CassandraPersistentProperty createPersistentProperty(Property property,
			BasicCassandraPersistentEntity<?> owner, SimpleTypeHolder simpleTypeHolder) {

		BasicCassandraPersistentProperty persistentProperty = owner.isTupleType()
				? new BasicCassandraPersistentTupleProperty(property, owner, simpleTypeHolder, getUserTypeResolver())
				: new BasicCassandraPersistentProperty(property, owner, simpleTypeHolder, getUserTypeResolver());

		Optional.ofNullable(this.applicationContext).ifPresent(persistentProperty::setApplicationContext);

		return persistentProperty;
	}

	/**
	 * Returns whether this mapping context has any entities mapped to the given table.
	 *
	 * @param name must not be {@literal null}.
	 * @return @return {@literal true} is this {@literal TableMetadata} is used by a mapping.
	 */
	public boolean usesTable(CqlIdentifier name) {

		Assert.notNull(name, "Table name must not be null");

		return this.entitySetsByTableName.containsKey(name);
	}

	/**
	 * Returns whether this mapping context has any entities using the given user type.
	 *
	 * @param name must not be {@literal null}.
	 * @return {@literal true} is this {@literal UserType} is used.
	 * @since 1.5
	 */
	public boolean usesUserType(CqlIdentifier name) {

		Assert.notNull(name, "User type name must not be null");

		return hasMappedUserType(name) || hasReferencedUserType(name);
	}

	private boolean hasMappedUserType(CqlIdentifier identifier) {
		return this.userDefinedTypes.stream().map(CassandraPersistentEntity::getTableName).anyMatch(identifier::equals);
	}

	private boolean hasReferencedUserType(CqlIdentifier identifier) {

		return getPersistentEntities().stream().flatMap(entity -> StreamSupport.stream(entity.spliterator(), false))
				.flatMap(it -> Optionals.toStream(Optional.ofNullable(it.findAnnotation(CassandraType.class))))
				.map(CassandraType::userTypeName) //
				.filter(StringUtils::hasText) //
				.map(CqlIdentifier::fromCql) //
				.anyMatch(identifier::equals);
	}

	/**
	 * Returns a {@link CreateTableSpecification} for the given entity, including all mapping information.
	 *
	 * @param entity must not be {@literal null}.
	 * @return
	 */
	public CreateTableSpecification getCreateTableSpecificationFor(CassandraPersistentEntity<?> entity) {

		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		return getCreateTableSpecificationFor(entity.getTableName(), entity);
	}

	/**
	 * Returns a {@link CreateTableSpecification} for the given entity using {@code tableName}, including all mapping
	 * information.
	 *
	 * @param tableName must not be {@literal null}.
	 * @param entity must not be {@literal null}.
	 * @return
	 * @since 2.2
	 */
	public CreateTableSpecification getCreateTableSpecificationFor(
			com.datastax.oss.driver.api.core.CqlIdentifier tableName, CassandraPersistentEntity<?> entity) {

		Assert.notNull(tableName, "Table name must not be null");
		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		CreateTableSpecification specification = createTable(tableName);

		for (CassandraPersistentProperty property : entity) {

			if (property.isCompositePrimaryKey()) {

				CassandraPersistentEntity<?> primaryKeyEntity = getRequiredPersistentEntity(property.getRawType());

				for (CassandraPersistentProperty primaryKeyProperty : primaryKeyEntity) {

					DataType dataType = getDataTypeWithUserTypeFactory(primaryKeyProperty, DataTypeProvider.ShallowType);

					if (primaryKeyProperty.isPartitionKeyColumn()) {
						specification.partitionKeyColumn(primaryKeyProperty.getRequiredColumnName(), dataType);
					} else { // cluster column
						specification.clusteredKeyColumn(primaryKeyProperty.getRequiredColumnName(), dataType,
								primaryKeyProperty.getPrimaryKeyOrdering());
					}
				}
			} else {
				DataType type = UserTypeUtil
						.potentiallyFreeze(getDataTypeWithUserTypeFactory(property, DataTypeProvider.ShallowType));

				if (property.isIdProperty() || property.isPartitionKeyColumn()) {
					specification.partitionKeyColumn(property.getRequiredColumnName(), type);
				} else if (property.isClusterKeyColumn()) {
					specification.clusteredKeyColumn(property.getRequiredColumnName(), type, property.getPrimaryKeyOrdering());
				} else {
					specification.column(property.getRequiredColumnName(), type);
				}
			}
		}

		if (specification.getPartitionKeyColumns().isEmpty()) {
			throw new MappingException(String.format("No partition key columns found in entity [%s]", entity.getType()));
		}

		return specification;
	}

	/**
	 * @param entity must not be {@literal null}.
	 * @return
	 * @since 2.0
	 */
	public List<CreateIndexSpecification> getCreateIndexSpecificationsFor(CassandraPersistentEntity<?> entity) {

		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		List<CreateIndexSpecification> indexes = new ArrayList<>();

		for (CassandraPersistentProperty property : entity) {
			if (property.isCompositePrimaryKey()) {
				indexes.addAll(getCreateIndexSpecificationsFor(getRequiredPersistentEntity(property)));
			} else {
				indexes.addAll(IndexSpecificationFactory.createIndexSpecifications(property));
			}
		}

		indexes.forEach(it -> it.tableName(entity.getTableName()));

		return indexes;
	}

	/**
	 * Returns a {@link CreateUserTypeSpecification} for the given entity, including all mapping information.
	 *
	 * @param entity must not be {@literal null}.
	 */
	public CreateUserTypeSpecification getCreateUserTypeSpecificationFor(CassandraPersistentEntity<?> entity) {

		Assert.notNull(entity, "CassandraPersistentEntity must not be null");

		CreateUserTypeSpecification specification = CreateUserTypeSpecification.createType(entity.getTableName());

		for (CassandraPersistentProperty property : entity) {
			// Use frozen literal to not resolve types from Cassandra; At this stage, they might be not created yet.
			specification.field(property.getRequiredColumnName(),
					getDataTypeWithUserTypeFactory(property, DataTypeProvider.FrozenLiteral));
		}

		if (specification.getFields().isEmpty()) {
			throw new MappingException(String.format("No fields in user type [%s]", entity.getType()));
		}

		return specification;
	}

	/**
	 * Retrieve the data type based on the given {@code type}. Cassandra {@link DataType types} are determined using
	 * simple types and configured {@link org.springframework.data.convert.CustomConversions}.
	 *
	 * @param type must not be {@literal null}.
	 * @return the Cassandra {@link DataType type}.
	 * @see org.springframework.data.convert.CustomConversions
	 * @see CassandraSimpleTypeHolder
	 * @since 1.5
	 */
	public DataType getDataType(Class<?> type) {
		return doGetDataType(type, this.customConversions.getCustomWriteTarget(type).orElse(type));
	}

	public TupleType getTupleType(CassandraPersistentEntity<?> persistentEntity) {

		Assert.notNull(persistentEntity, "CassandraPersistentEntity must not be null");
		Assert.isTrue(persistentEntity.isTupleType(), "CassandraPersistentEntity is not a mapped tuple type");

		return getTupleType(DataTypeProvider.EntityUserType, persistentEntity);
	}

	/**
	 * Retrieve the data type of the property. Cassandra {@link DataType types} are determined using simple types and
	 * configured {@link org.springframework.data.convert.CustomConversions}.
	 *
	 * @param property must not be {@literal null}.
	 * @return the Cassandra {@link DataType type}.
	 * @see org.springframework.data.convert.CustomConversions
	 * @see CassandraSimpleTypeHolder
	 * @since 1.5
	 */
	public DataType getDataType(CassandraPersistentProperty property) {
		return getDataTypeWithUserTypeFactory(property, DataTypeProvider.EntityUserType);
	}

	private DataType getDataTypeWithUserTypeFactory(CassandraPersistentProperty property,
			DataTypeProvider dataTypeProvider) {

		if (property.isAnnotationPresent(CassandraType.class)) {

			CassandraType annotation = property.getRequiredAnnotation(CassandraType.class);

			if (annotation.type() == Name.TUPLE) {

				DataType[] dataTypes = Arrays.stream(annotation.typeArguments()).map(CassandraSimpleTypeHolder::getDataTypeFor)
						.toArray(DataType[]::new);

				return getTupleTypeFactory().create(dataTypes);
			}

			if (annotation.type() == Name.UDT) {

				com.datastax.oss.driver.api.core.CqlIdentifier userTypeName = com.datastax.oss.driver.api.core.CqlIdentifier
						.fromCql(annotation.userTypeName());

				DataType userType = dataTypeProvider.getUserType(userTypeName, resolveUserTypeResolver());

				if (userType == null) {
					throw new MappingException(String.format("User type [%s] not found", userTypeName));
				}

				DataType dataType = getUserDataType(property.getTypeInformation(), userType);

				if (dataType != null) {
					return dataType;
				}
			}

			return property.getDataType();
		}

		if (TupleValue.class.isAssignableFrom(property.getType())) {
			throw new MappingException(String.format(
					"Unsupported raw TupleType to DataType for property [%s] in entity [%s]; Consider adding @CassandraType.",
					property.getName(), property.getOwner().getName()));
		}

		if (UdtValue.class.isAssignableFrom(property.getType())) {
			throw new MappingException(String.format(
					"Unsupported raw UdtValue to DataType for property [%s] in entity [%s]; Consider adding @CassandraType.",
					property.getName(), property.getOwner().getName()));
		}

		try {
			DataType dataType = getDataTypeWithUserTypeFactory(property.getTypeInformation(), dataTypeProvider,
					property::getDataType);

			if (dataType == null) {
				throw new MappingException(
						String.format("Cannot resolve DataType for property [%s] in entity [%s]; Consider adding @CassandraType.",
								property.getName(), property.getOwner().getName()));
			}

			return dataType;
		} catch (InvalidDataAccessApiUsageException e) {
			throw new MappingException(String.format("%s. Consider adding @CassandraType.", e.getMessage()), e);
		}
	}

	@Nullable
	private DataType getDataTypeWithUserTypeFactory(TypeInformation<?> typeInformation, DataTypeProvider dataTypeProvider,
			Supplier<DataType> fallback) {

		Optional<DataType> customWriteTarget = this.customConversions.getCustomWriteTarget(typeInformation.getType())
				.map(it -> doGetDataType(typeInformation.getType(), it));

		DataType dataType = customWriteTarget.orElseGet(() -> {

			Class<?> propertyType = typeInformation.getRequiredActualType().getType();

			return this.customConversions.getCustomWriteTarget(propertyType).filter(it -> !typeInformation.isMap())
					.map(it -> {

						if (typeInformation.isCollectionLike()) {
							if (List.class.isAssignableFrom(typeInformation.getType())) {
								return DataTypes.listOf(doGetDataType(propertyType, it));
							}

							if (Set.class.isAssignableFrom(typeInformation.getType())) {
								return DataTypes.setOf(doGetDataType(propertyType, it));
							}
						}

						return doGetDataType(propertyType, it);

					}).orElse(null);
		});

		if (dataType != null) {
			return dataType;
		}

		if (typeInformation.isCollectionLike()) {

			TypeInformation<?> componentType = typeInformation.getRequiredActualType();
			BasicCassandraPersistentEntity<?> persistentEntity = getPersistentEntity(componentType);
			TypeInformation<?> typeToUse = persistentEntity != null ? persistentEntity.getTypeInformation() : componentType;

			if (List.class.isAssignableFrom(typeInformation.getType())) {
				return DataTypes.listOf(getDataTypeWithUserTypeFactory(typeToUse, dataTypeProvider, fallback));
			}

			if (Set.class.isAssignableFrom(typeInformation.getType())) {
				return DataTypes.setOf(getDataTypeWithUserTypeFactory(typeToUse, dataTypeProvider, fallback));
			}

			throw new IllegalArgumentException("Unsupported collection type: " + typeInformation);
		}

		if (typeInformation.isMap()) {
			return getMapDataType(typeInformation, dataTypeProvider);
		}

		BasicCassandraPersistentEntity<?> persistentEntity = getPersistentEntity(typeInformation);

		if (persistentEntity != null) {

			if (persistentEntity.isUserDefinedType()) {

				DataType udtType = dataTypeProvider.getDataType(persistentEntity);

				if (udtType != null) {
					return udtType;
				}
			} else if (persistentEntity.isTupleType()) {
				return getTupleType(dataTypeProvider, persistentEntity);
			}

			return dataTypeProvider.getDataType(persistentEntity);
		}

		DataType determinedType = doGetDataType(typeInformation);

		if (determinedType != null) {
			return determinedType;
		}

		return fallback.get();
	}

	/**
	 * Resolve Cassandra {@link DataType}.
	 *
	 * @param typeInformation
	 * @return
	 */
	@Nullable
	private DataType doGetDataType(TypeInformation<?> typeInformation) {
		return doGetDataType(typeInformation.getType(), typeInformation.getType());
	}

	/**
	 * Resolve Cassandra {@link DataType} with conditional handling of {@code time} data type.
	 *
	 * @param propertyType
	 * @param converted
	 * @return
	 */
	@Nullable
	private DataType doGetDataType(Class<?> propertyType, Class<?> converted) {

		if (this.customConversions instanceof CassandraCustomConversions) {

			CassandraCustomConversions conversions = (CassandraCustomConversions) this.customConversions;

			if (conversions.isNativeTimeTypeMarker(converted)
					|| (conversions.isNativeTimeTypeMarker(propertyType) && Long.class.equals(converted))) {

				return DataTypes.TIME;
			}
		}

		return CassandraSimpleTypeHolder.getDataTypeFor(converted);
	}

	private TupleType getTupleType(DataTypeProvider dataTypeProvider, CassandraPersistentEntity<?> persistentEntity) {

		List<DataType> types = new ArrayList<>();

		for (CassandraPersistentProperty persistentProperty : persistentEntity) {
			types.add(getDataTypeWithUserTypeFactory(persistentProperty, dataTypeProvider));
		}

		return getTupleTypeFactory().create(types);
	}

	@SuppressWarnings("all")
	private DataType getMapDataType(TypeInformation<?> typeInformation, DataTypeProvider dataTypeProvider) {

		TypeInformation<?> keyTypeInformation = typeInformation.getComponentType();
		TypeInformation<?> valueTypeInformation = typeInformation.getMapValueType();

		DataType keyType = getDataTypeWithUserTypeFactory(keyTypeInformation, dataTypeProvider, () -> {

			DataType type = doGetDataType(keyTypeInformation.getType(), keyTypeInformation.getType());

			if (type != null) {
				return type;
			}

			throw new MappingException(String.format("Cannot resolve key type for [%s]", typeInformation));
		});

		DataType valueType = getDataTypeWithUserTypeFactory(valueTypeInformation, dataTypeProvider, () -> {

			DataType type = doGetDataType(valueTypeInformation.getType(), valueTypeInformation.getType());

			if (type != null) {
				return type;
			}

			throw new MappingException("Cannot resolve value type for " + typeInformation + ".");
		});

		return DataTypes.mapOf(keyType, valueType);
	}

	@Nullable
	private DataType getUserDataType(TypeInformation<?> property, @Nullable DataType elementType) {

		if (property.isCollectionLike()) {

			if (List.class.isAssignableFrom(property.getType())) {
				return DataTypes.listOf(elementType);
			}

			if (Set.class.isAssignableFrom(property.getType())) {
				return DataTypes.setOf(elementType);
			}
		}

		return !(property.isCollectionLike() || property.isMap()) ? elementType : null;
	}

	/**
	 * @author Jens Schauder
	 * @author Mark Paluch
	 * @since 1.5.1
	 */
	enum DataTypeProvider {

		EntityUserType {

			@Override
			public DataType getDataType(CassandraPersistentEntity<?> entity) {
				return entity.isTupleType() ? entity.getTupleType() : entity.getUserType();
			}

			@Override
			DataType getUserType(com.datastax.oss.driver.api.core.CqlIdentifier userTypeName,
					UserTypeResolver userTypeResolver) {
				return userTypeResolver.resolveType(userTypeName);
			}
		},

		ShallowType {

			@Override
			public DataType getDataType(CassandraPersistentEntity<?> entity) {
				return entity.isTupleType() ? entity.getTupleType() : new ShallowUserDefinedType(entity.getTableName(), false);
			}

			@Override
			DataType getUserType(com.datastax.oss.driver.api.core.CqlIdentifier userTypeName,
					UserTypeResolver userTypeResolver) {
				return new ShallowUserDefinedType(userTypeName, false);
			}
		},

		FrozenLiteral {

			@Override
			public DataType getDataType(CassandraPersistentEntity<?> entity) {
				return new ShallowUserDefinedType(entity.getTableName(), true);
			}

			@Override
			DataType getUserType(com.datastax.oss.driver.api.core.CqlIdentifier userTypeName,
					UserTypeResolver userTypeResolver) {
				return new ShallowUserDefinedType(userTypeName, true);
			}
		};

		/**
		 * Return the data type for the {@link CassandraPersistentEntity}.
		 *
		 * @param entity must not be {@literal null}.
		 * @return the {@link DataType}.
		 */
		@Nullable
		abstract DataType getDataType(CassandraPersistentEntity<?> entity);

		/**
		 * Return the user-defined type {@code userTypeName}.
		 *
		 * @param userTypeName must not be {@literal null}.
		 * @param userTypeResolver must not be {@literal null}.
		 * @return the {@link DataType}.
		 * @since 2.0.1
		 */
		@Nullable
		abstract DataType getUserType(com.datastax.oss.driver.api.core.CqlIdentifier userTypeName,
				UserTypeResolver userTypeResolver);

	}

	static class ShallowUserDefinedType implements com.datastax.oss.driver.api.core.type.UserDefinedType {

		private final CqlIdentifier name;
		private final boolean frozen;

		public ShallowUserDefinedType(String name, boolean frozen) {
			this(CqlIdentifier.fromInternal(name), frozen);
		}

		public ShallowUserDefinedType(CqlIdentifier name, boolean frozen) {
			this.name = name;
			this.frozen = frozen;
		}

		@Override
		public CqlIdentifier getKeyspace() {
			return null;
		}

		@Override
		public CqlIdentifier getName() {
			return name;
		}

		@Override
		public boolean isFrozen() {
			return frozen;
		}

		@Override
		public List<CqlIdentifier> getFieldNames() {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public int firstIndexOf(CqlIdentifier id) {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public int firstIndexOf(String name) {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public List<DataType> getFieldTypes() {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public com.datastax.oss.driver.api.core.type.UserDefinedType copy(boolean newFrozen) {
			return new ShallowUserDefinedType(this.name, newFrozen);
		}

		@Override
		public UdtValue newValue() {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public UdtValue newValue(@edu.umd.cs.findbugs.annotations.NonNull Object... fields) {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public AttachmentPoint getAttachmentPoint() {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public boolean isDetached() {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public void attach(@edu.umd.cs.findbugs.annotations.NonNull AttachmentPoint attachmentPoint) {
			throw new UnsupportedOperationException(
					"This implementation should only be used internally, this is likely a driver bug");
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof com.datastax.oss.driver.api.core.type.UserDefinedType))
				return false;
			com.datastax.oss.driver.api.core.type.UserDefinedType that = (com.datastax.oss.driver.api.core.type.UserDefinedType) o;
			return isFrozen() == that.isFrozen() && Objects.equals(getName(), that.getName());
		}

		@Override
		public int hashCode() {
			return Objects.hash(name, frozen);
		}

		@Override
		public String toString() {
			return "ShallowUserDefinedType{" + "name=" + name + ", frozen=" + frozen + '}';
		}
	}
}
