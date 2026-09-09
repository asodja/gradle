/*
 * Copyright 2026 the original author or authors.
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

@file:JvmName("PropertyAssignmentCallSites")

package org.gradle.kotlin.dsl.internal

import org.gradle.api.internal.provider.PropertyCallSites
import org.gradle.api.internal.provenance.SourceLocation
import org.gradle.api.provider.HasMultipleValues
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.assign


// Keep Kotlin assignment semantics in the existing extension; only scope diagnostic metadata here.
fun <T : Any> assign(property: Property<T>, value: T?, location: SourceLocation?) {
    PropertyCallSites.withLocation(property, location) { property.assign(value) }
}


fun <T : Any> assign(property: Property<T>, value: Provider<out T>, location: SourceLocation?) {
    PropertyCallSites.withLocation(property, location) { property.assign(value) }
}


fun <T : Any> assign(property: HasMultipleValues<T>, elements: Iterable<T>?, location: SourceLocation?) {
    PropertyCallSites.withLocation(property, location) { property.assign(elements) }
}


fun <T : Any> assign(property: HasMultipleValues<T>, provider: Provider<out Iterable<T>>, location: SourceLocation?) {
    PropertyCallSites.withLocation(property, location) { property.assign(provider) }
}


fun <K : Any, V : Any> assign(property: MapProperty<K, V>, entries: Map<out K, V>?, location: SourceLocation?) {
    PropertyCallSites.withLocation(property, location) { property.assign(entries) }
}


fun <K : Any, V : Any> assign(property: MapProperty<K, V>, provider: Provider<out Map<out K, V>>, location: SourceLocation?) {
    PropertyCallSites.withLocation(property, location) { property.assign(provider) }
}
