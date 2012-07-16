/*
 * Copyright 2010-2012 the original author or authors.
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
package org.springframework.data.cassandra.core.convert;

import org.springframework.data.cassandra.core.CassandraDataObject;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentProperty;
import org.springframework.data.convert.EntityWriter;

/**
 * A CassandraWriter is responsible for converting an object of type T to the native CassandraDataObject.
 * 
 * @param <T> the type of the object to convert to a CassandraDataObject
 * @author Brian O'Neill
 */
public interface CassandraWriter<T> extends EntityWriter<T, CassandraDataObject> {

	/**
	 * Converts the given object into one Cassandra will be able to store natively. If the given object can already be stored
	 * as is, no conversion will happen.
	 * 
	 * @param obj
	 * @return
	 */
	Object convertToCassandraType(Object obj);

	/**
	 * Creates a {@link DBRef} to refer to the given object.
	 * 
	 * @param object the object to create a {@link DBRef} to link to. The object's type has to carry an id attribute.
	 * @param referingProperty the client-side property referring to the object which might carry additional metadata for
	 *          the {@link DBRef} object to create. Can be {@literal null}.
	 * @return will never be {@literal null}.
	 */
	CassandraDataObject toCassandraDataObject(Object object, CassandraPersistentProperty referingProperty);
}
