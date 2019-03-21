/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.data.cassandra.repository.config;

import java.util.Collection;
import java.util.Collections;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.data.cassandra.repository.support.ReactiveCassandraRepositoryFactoryBean;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.util.StringUtils;

/**
 * {@link RepositoryConfigurationExtension} for Cassandra.
 *
 * @author Mark Paluch
 * @since 2.0
 */
public class ReactiveCassandraRepositoryConfigurationExtension extends CassandraRepositoryConfigurationExtension {

	/**
	 * @inheritDoc
	 */
	@Override
	public String getModuleName() {
		return "Reactive Cassandra";
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public String getRepositoryFactoryClassName() {
		return ReactiveCassandraRepositoryFactoryBean.class.getName();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void postProcess(BeanDefinitionBuilder builder, AnnotationRepositoryConfigurationSource config) {

		String reactiveCassandraTemplateRef = config.getAttributes().getString("reactiveCassandraTemplateRef");

		if (StringUtils.hasText(reactiveCassandraTemplateRef)) {
			builder.addPropertyReference("reactiveCassandraOperations", reactiveCassandraTemplateRef);
		}
	}

	/**
	 * @inheritDoc
	 */
	@Override
	protected Collection<Class<?>> getIdentifyingTypes() {
		return Collections.singleton(ReactiveCassandraRepository.class);
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport#useRepositoryConfiguration(org.springframework.data.repository.core.RepositoryMetadata)
	 */
	protected boolean useRepositoryConfiguration(RepositoryMetadata metadata) {
		return metadata.isReactiveRepository();
	}
}
