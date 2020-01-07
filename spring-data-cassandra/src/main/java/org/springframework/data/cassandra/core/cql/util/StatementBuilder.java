/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.data.cassandra.core.cql.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.springframework.util.Assert;

import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatementBuilder;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.querybuilder.BuildableQuery;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

/**
 * Functional builder for Cassandra {@link BuildableQuery statements}. Statements are built by applying
 * {@link UnaryOperator builder functions} that get applied when {@link #build() building the actual
 * {@link SimpleStatement statement}. The {@code StatmentBuilder} provides a mutable container for statement creation
 * allowing a functional declaration of actions that are necessary to build a statement. This class helps building CQL
 * statements as a {@link BuildableQuery} classes are typically immutable and require return value tracking across
 * methods that want to apply modifications to a statment.
 * <p>
 * Building a statement consists of three phases:
 * <ol>
 * <li>Creation of the {@link StatementBuilder} with a {@link BuildableQuery query stub}</li>
 * <li>Functional declaration applying {@link UnaryOperator builder functions}, {@link BindFunction bind functions} and
 * {@link Consumer on build signals}</li>
 * <li>Building the statement using {@link #build()}</li>
 * </ol>
 * The initial {@link BuildableQuery query stub} is used as base object for all built queries. Builder functions are
 * applied each time a statement is built allowing to build multiple statement instances while evolving the actual
 * statement.
 * <p>
 * The builder can be used for structural evolution and value evolution of statements. Values are bound through
 * {@link BindFunction binding functions} that accept the statement and a {@link TermFactory}. Values can be bound
 * inline or through bind markers when {@link #build(ParameterHandling, CodecRegistry) building} the statement. All
 * functions are applied in the order of their declaration.
 * <p>
 * All methods returning {@link StatementBuilder} point to the same instance. This class is intended for internal use.
 *
 * @author Mark Paluch
 * @param <S> Statement type
 * @since 3.0
 */
public class StatementBuilder<S extends BuildableQuery> {

	private S statement;

	private List<BuilderRunnable<S>> queryActions = new ArrayList<>();
	private List<Consumer<SimpleStatementBuilder>> onBuild = new ArrayList<>();

	private StatementBuilder(S statement) {
		this.statement = statement;
	}

	/**
	 * Create a new {@link StatementBuilder} with the given {@link BuildableQuery query stub}. The stub is used as base
	 * for the built query so each query inherits properties of this stub.
	 *
	 * @param stub the query stub to use.
	 * @param <S> query type.
	 * @return the {@link StatementBuilder} for the {@link BuildableQuery query stub}.
	 */
	public static <S extends BuildableQuery> StatementBuilder<S> of(S stub) {

		Assert.notNull(stub, "Query stub must not be null");

		return new StatementBuilder<>(stub);
	}

	/**
	 * Apply a {@link BindFunction} to the statement. Bind functions are applied on {@link #build()}.
	 *
	 * @param action the bind function to be applied to the statement.
	 * @return {@code this} {@link StatementBuilder}.
	 */
	public StatementBuilder<S> bind(BindFunction<S> action) {

		Assert.notNull(action, "BindFunction must not be null");

		queryActions.add(action::bind);
		return this;
	}

	/**
	 * Apply a {@link UnaryOperator builder function} to the statement. Builder functions are applied on {@link #build()}.
	 *
	 * @param action the builder function to be applied to the statement.
	 * @return {@code this} {@link StatementBuilder}.
	 */
	public <R extends BuildableQuery> StatementBuilder<S> apply(Function<S, R> action) {

		Assert.notNull(action, "BindFunction must not be null");

		queryActions.add((source, termFactory) -> (S) action.apply(source));
		return this;
	}

	/**
	 * Add behavior when the statement is built. The {@link Consumer} gets invoked with a {@link SimpleStatementBuilder}
	 * allowing association of the final statement with additional settings. The {@link Consumer} is applied on
	 * {@link #build()}.
	 *
	 * @param action the {@link Consumer} function that gets notified on {@link #build()}.
	 * @return {@code this} {@link StatementBuilder}.
	 */
	public StatementBuilder<S> onBuild(Consumer<SimpleStatementBuilder> action) {

		Assert.notNull(action, "Consumer must not be null");

		onBuild.add(action);
		return this;
	}

	/**
	 * Build a {@link SimpleStatement statement} by applying builder and bind functions using the default
	 * {@link CodecRegistry} and {@link ParameterHandling#INLINE} parameter rendering.
	 *
	 * @return the built {@link SimpleStatement}.
	 */
	public SimpleStatement build() {
		return build(ParameterHandling.INLINE, CodecRegistry.DEFAULT);
	}

	/**
	 * Build a {@link SimpleStatement statement} by applying builder and bind functions using the given
	 * {@link ParameterHandling}.
	 *
	 * @param parameterHandling
	 * @return the built {@link SimpleStatement}.
	 */
	public SimpleStatement build(ParameterHandling parameterHandling) {
		return build(parameterHandling, CodecRegistry.DEFAULT);
	}

	/**
	 * Build a {@link SimpleStatement statement} by applying builder and bind functions using the given
	 * {@link CodecRegistry} and {@link ParameterHandling}.
	 *
	 * @param parameterHandling
	 * @param codecRegistry
	 * @return the built {@link SimpleStatement}.
	 */
	public SimpleStatement build(ParameterHandling parameterHandling, CodecRegistry codecRegistry) {

		Assert.notNull(parameterHandling, "ParameterHandling must not be null");
		Assert.notNull(codecRegistry, "CodecRegistry must not be null");

		S statement = this.statement;

		if (parameterHandling == ParameterHandling.INLINE) {

			TermFactory termFactory = value -> QueryBuilder.literal(value, codecRegistry);

			for (BuilderRunnable<S> runnable : queryActions) {
				statement = runnable.run(statement, termFactory);
			}

			return onBuild(statement.builder()).build();
		}

		if (parameterHandling == ParameterHandling.BY_INDEX) {

			List<Object> values = new ArrayList<>();
			TermFactory termFactory = value -> {
				values.add(value);
				return QueryBuilder.bindMarker();
			};

			for (BuilderRunnable<S> runnable : queryActions) {
				statement = runnable.run(statement, termFactory);
			}

			return onBuild(statement.builder().addPositionalValues(values)).build();
		}

		if (parameterHandling == ParameterHandling.BY_NAME) {

			Map<String, Object> values = new LinkedHashMap<>();
			TermFactory termFactory = value -> {
				String name = "p" + values.size();
				values.put(name, value);
				return QueryBuilder.bindMarker(name);
			};

			for (BuilderRunnable<S> runnable : queryActions) {
				statement = runnable.run(statement, termFactory);
			}

			SimpleStatementBuilder builder = statement.builder();
			values.forEach(builder::addNamedValue);

			return onBuild(builder).build();
		}

		throw new UnsupportedOperationException(String.format("ParameterHandling %s not supported", parameterHandling));
	}

	private SimpleStatementBuilder onBuild(SimpleStatementBuilder statementBuilder) {

		onBuild.forEach(it -> it.accept(statementBuilder));

		return statementBuilder;
	}

	/**
	 * Binding function. This function gets called with the current statement and {@link TermFactory}.
	 *
	 * @param <S>
	 */
	@FunctionalInterface
	public interface BindFunction<S> {

		/**
		 * Apply a binding operation on the {@link BuildableQuery statement} and return the modified statement instance.
		 *
		 * @param statement the initial statement instance.
		 * @param factory factory to create {@link com.datastax.oss.driver.api.querybuilder.term.Term} objects.
		 * @return the modified statement instance.
		 */
		S bind(S statement, TermFactory factory);
	}

	@FunctionalInterface
	interface BuilderRunnable<S> {
		S run(S source, TermFactory termFactory);
	}

	/**
	 * Enumeration to represent how parameters are rendered.
	 */
	public enum ParameterHandling {

		/**
		 * CQL inline rendering as literals.
		 */
		INLINE,

		/**
		 * Index-based bind markers.
		 */
		BY_INDEX,

		/**
		 * Named bind markers.
		 */
		BY_NAME;
	}
}
