/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Task;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.StringJoiner;

/**
 * The base implementation for all properties in Gradle.
 * <p>
 *     A property is a provider where the value is configurable.
 * </p>
 * <p>
 *     A property's value is not stored in the property itself,
 *     but computed by some {@link ValueSupplier}, which
 *     provides the basic machinery for lazy evaluation.
 * </p>
 *
 * @param <T> the type of the value this property provides
 * @param <S> the type of value supplier that actually provides the value for this property
 */
public abstract class AbstractProperty<T, S extends ValueSupplier> extends AbstractMinimalProvider<T> implements PropertyInternal<T> {
    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this property");
    private static final DisplayName DEFAULT_VALIDATION_DISPLAY_NAME = Describables.of("a property");

    private ModelObject producer;
    private DisplayName displayName;
    private ValueState<S> state;
    private S value;
    private CollaborativeState collaborativeState;

    public AbstractProperty(PropertyHost host) {
        state = ValueState.newState(host);
    }

    protected void init(S initialValue, S convention) {
        this.value = initialValue;
        this.state.setConvention(convention);
    }

    protected void init(S initialValue) {
        init(initialValue, initialValue);
    }

    @Override
    public boolean isFinalized() {
        return state.isFinalized();
    }

    public boolean isDisallowChanges() {
        return state.isDisallowChanges();
    }

    @Override
    public boolean isCollaborative() {
        return collaborativeState != null;
    }

    @Override
    public void enableCollaboration(String owner, List<String> contributorOrder) {
        assertCanMutate();
        if (collaborativeState != null) {
            throw new IllegalStateException("Collaborative mode is already enabled for '" + getDisplayName().getDisplayName() + "'.");
        }
        collaborativeState = new CollaborativeState(owner, contributorOrder);
    }

    @Override
    public void addCollaborationConstraint(String before, String after) {
        assertCanMutate();
        requireCollaborativeState().addConstraint(before, after);
    }

    protected boolean isExplicit() {
        return state.isExplicit();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeRead(context, consumer); // may throw its own exception, which should not be wrapped.
            try {
                if (usesCollaborativePipeline()) {
                    return collaborativeState.updatePipeline.calculatePresence(consumer);
                }
                return getSupplier(context).calculatePresence(consumer);
            } catch (Exception e) {
                if (displayName != null) {
                    throw new PropertyQueryException(String.format("Failed to query the value of %s.", displayName), e);
                } else {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }

    @Override
    public void attachOwner(@Nullable ModelObject owner, DisplayName displayName) {
        this.displayName = displayName;
    }

    @Nullable
    @Override
    protected DisplayName getDeclaredDisplayName() {
        return displayName;
    }

    @Override
    protected DisplayName getTypedDisplayName() {
        return DEFAULT_DISPLAY_NAME;
    }

    @Override
    protected DisplayName getDisplayName() {
        if (displayName == null) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName;
    }

    protected DisplayName getValidationDisplayName() {
        if (displayName == null) {
            return DEFAULT_VALIDATION_DISPLAY_NAME;
        }
        return displayName;
    }

    @Override
    public void attachProducer(ModelObject owner) {
        if (this.producer == null) {
            this.producer = owner;
        } else if (this.producer != owner) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(getDisplayName().getCapitalizedDisplayName());
            formatter.append(" is already declared as an output property of ");
            format(this.producer, formatter);
            formatter.append(". Cannot also declare it as an output property of ");
            format(owner, formatter);
            formatter.append(".");
            throw new IllegalStateException(formatter.toString());
        }
    }

    protected final S getSupplier(EvaluationScopeContext ignored) {
        // context serves as a token here to ensure that the scope is opened.
        return value;
    }

    protected S getConventionSupplier() {
        return state.convention();
    }

    protected final String describeValue() {
        return value.toString();
    }

    protected Value<? extends T> calculateOwnValueNoProducer(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeReadNoProducer(context, consumer);
            return doCalculateValue(context, consumer);
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeRead(context, consumer);
            return doCalculateValue(context, consumer);
        }
    }

    @NonNull
    private Value<? extends T> doCalculateValue(EvaluationScopeContext context, ValueConsumer consumer) {
        try {
            if (usesCollaborativePipeline()) {
                return collaborativeState.updatePipeline.calculateValue(consumer);
            }
            return calculateValueFrom(context, value, consumer);
        } catch (Exception e) {
            if (displayName != null) {
                throw new PropertyQueryException(String.format("Failed to query the value of %s.", displayName), e);
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    /**
     * Throws a {@link ConcurrentModificationException} with text describing data corruption because of unsafe property access.
     *
     * @param reason the (optional) reason indicating the detected corruption
     * @return nothing (it throws), but you can use this method in {@code throw} statement to appease the compiler
     */
    protected ConcurrentModificationException failWithCorruptedStateException(@Nullable Throwable reason) {
        throw new ConcurrentModificationException(
            "State of " + getDisplayName().getDisplayName() + " is corrupted. " +
                "This may be caused by unsafe concurrent modifications with parallel configuration or execution enabled.",
            reason
        );
    }

    protected abstract Value<? extends T> calculateValueFrom(EvaluationScopeContext context, S value, ValueConsumer consumer);

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext context = openScope()) {
            validateCollaborativeOrder();
            ExecutionTimeValue<? extends T> value = usesCollaborativePipeline()
                ? collaborativeState.updatePipeline.calculateExecutionTimeValue()
                : calculateOwnExecutionTimeValue(context, this.value);
            if (getProducerTask() == null) {
                return value;
            } else {
                return value.withChangingContent();
            }
        }
    }

    protected abstract ExecutionTimeValue<? extends T> calculateOwnExecutionTimeValue(EvaluationScopeContext context, S value);

    /**
     * Returns a diagnostic string describing the current source of value of this property. Should not realize the value.
     */
    protected abstract String describeContents();

    // This method is final - implement describeContents() instead
    @Override
    protected final String toStringNoReentrance() {
        if (displayName != null) {
            return displayName.toString();
        } else {
            return describeContents();
        }
    }

    @Override
    public ValueProducer getProducer() {
        Task task = getProducerTask();
        if (task != null) {
            return ValueProducer.task(task);
        } else {
            try (EvaluationScopeContext context = openScope()) {
                if (usesCollaborativePipeline()) {
                    return collaborativeState.updatePipeline.getProducer();
                }
                return getSupplier(context).getProducer();
            }
        }
    }

    @Override
    public void finalizeValue() {
        validateCollaborativeOrder();
        if (state.shouldFinalize(this.getDisplayName(), producer)) {
            try (EvaluationScopeContext context = openScope()) {
                finalizeNow(context, ValueConsumer.IgnoreUnsafeRead);
            }
        }
    }

    @Override
    public void disallowChanges() {
        validateCollaborativeOrder();
        state.disallowChanges();
    }

    @Override
    public void finalizeValueOnRead() {
        validateCollaborativeOrder();
        state.finalizeOnNextGet();
    }

    @Override
    public void implicitFinalizeValue() {
        validateCollaborativeOrder();
        if (state.isUpgradedPropertyValue()) {
            // Upgraded properties should not be finalized to simplify migration.
            // This behaviour should be removed with Gradle 10.
            state.warnOnUpgradedPropertyValueChanges();
        } else {
            state.disallowChangesAndFinalizeOnNextGet();
        }
    }

    @Override
    public void markAsUpgradedProperty() {
        state.markAsUpgradedPropertyValue();
    }

    @Override
    public void disallowUnsafeRead() {
        state.disallowUnsafeRead();
    }

    protected abstract S finalValue(EvaluationScopeContext context, S value, ValueConsumer consumer);

    /**
     * Adapts a provider of the property's public value type to its internal supplier type.
     */
    protected abstract S supplierFromProvider(ProviderInternal<? extends T> provider);

    /**
     * Assigns a provider either as an ordinary property value, as a declarative source, or as an
     * attributed collaborative self-update, depending on the property's mode and active context.
     */
    protected final void setProviderValue(ProviderInternal<? extends T> provider) {
        if (collaborativeState == null) {
            setSupplier(supplierFromProvider(substituteSelfReference(provider)));
            return;
        }

        CollaborativePropertyContext.Mutation mutation = requireCollaborativeMutation();
        if (mutation.isSource()) {
            setSupplier(supplierFromProvider(provider));
            return;
        }

        assertCanMutate();
        collaborativeState.acceptUpdate(mutation, provider);
    }

    protected void setSupplier(S supplier) {
        assertCollaborativeSourceMutation();
        assertCanMutate();
        this.value = state.explicitValue(supplier);
    }

    protected void setConvention(S convention) {
        assertCollaborativeConventionMutation();
        assertCanMutate();
        this.value = state.applyConvention(value, convention);
    }

    /**
     * Call prior to reading the value of this property.
     */
    protected void beforeRead(EvaluationScopeContext context, ValueConsumer consumer) {
        beforeRead(context, producer, consumer);
    }

    protected void beforeReadNoProducer(EvaluationScopeContext context, ValueConsumer consumer) {
        beforeRead(context, null, consumer);
    }

    private void beforeRead(EvaluationScopeContext context, @Nullable ModelObject effectiveProducer, ValueConsumer consumer) {
        validateCollaborativeOrder();
        state.finalizeOnReadIfNeeded(this.getDisplayName(), effectiveProducer, consumer, effectiveConsumer -> finalizeNow(context, effectiveConsumer));
    }

    private void finalizeNow(EvaluationScopeContext context, ValueConsumer consumer) {
        try {
            S valueToFinalize = usesCollaborativePipeline()
                ? supplierFromProvider(collaborativeState.updatePipeline)
                : value;
            value = finalValue(context, valueToFinalize, state.forUpstream(consumer));
        } catch (Exception e) {
            if (displayName != null) {
                throw new PropertyQueryException(String.format("Failed to calculate the value of %s.", displayName), e);
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        state = state.finalState();
    }

    /**
     * Returns the current value of this property, if explicitly defined, otherwise the given default. Does not apply the convention.
     */
    protected S getExplicitValue(S defaultValue) {
        return state.explicitValue(value, defaultValue);
    }

    /**
     * Discards the value of this property, and uses its convention.
     */
    protected void discardValue() {
        assertCollaborativeSourceMutation();
        assertCanMutate();
        if (isDefaultConvention()) {
            // special case: discarding value without a convention restores the initial state
            state.implicitValue(getDefaultConvention());
            value = getDefaultValue();
        } else {
            // otherwise, the convention will become the new value
            value = state.implicitValue(state.convention());
        }
    }

    /**
     * Discards the convention of this property.
     */
    protected void discardConvention() {
        assertCollaborativeConventionMutation();
        assertCanMutate();
        value = state.applyConvention(value, getDefaultConvention());
    }

    @Override
    public SupportsConvention unsetConvention() {
        discardConvention();
        return this;
    }

    @Override
    public SupportsConvention unset() {
        discardValue();
        return this;
    }

    /**
     * Sets the value of the property to the current convention value, replacing whatever explicit value the property already had.
     *
     * If the property has no convention set at the time this method is invoked,
     * the effect of invoking it is similar to invoking {@link #unset()}.
     */
    protected SupportsConvention setToConvention() {
        assertCollaborativeSourceMutation();
        assertCanMutate();
        this.value = state.setToConvention();
        return this;
    }

    /**
     * Sets the value of the property to the current convention value, if an explicit
     * value has not been set yet.
     *
     * If the property has no convention set at the time this method is invoked,
     * or if an explicit value has already been set, it has no effect.
     */
    protected SupportsConvention setToConventionIfUnset() {
        assertCollaborativeSourceMutation();
        assertCanMutate();
        if (!isDefaultConvention()) {
            this.value = state.setToConventionIfUnset(value);
        }
        return this;
    }

    protected abstract S getDefaultValue();

    protected abstract S getDefaultConvention();

    /**
     * Is convention set to the initial convention value?
     */
    protected abstract boolean isDefaultConvention();

    private boolean usesCollaborativePipeline() {
        return collaborativeState != null && !state.isFinalized();
    }

    private CollaborativeState requireCollaborativeState() {
        if (collaborativeState == null) {
            throw new IllegalStateException("Collaborative mode is not enabled for '" + getDisplayName().getDisplayName() + "'.");
        }
        return collaborativeState;
    }

    private CollaborativePropertyContext.Mutation requireCollaborativeMutation() {
        CollaborativePropertyContext.Mutation mutation = CollaborativePropertyContext.currentMutation();
        if (mutation == null) {
            throw new IllegalStateException("Cannot mutate collaborative property '" + getDisplayName().getDisplayName()
                + "': no declarative source or contributor context is active.");
        }
        return mutation;
    }

    private void assertCollaborativeSourceMutation() {
        if (collaborativeState == null) {
            return;
        }
        CollaborativePropertyContext.Mutation mutation = requireCollaborativeMutation();
        if (!mutation.isSource()) {
            throw new IllegalStateException("Cannot replace collaborative property '" + getDisplayName().getDisplayName()
                + "' from contributor '" + mutation.getContributor() + "'; contributors may only apply structural self-updates.");
        }
    }

    private void assertCollaborativeConventionMutation() {
        if (collaborativeState == null) {
            return;
        }
        CollaborativePropertyContext.Mutation mutation = requireCollaborativeMutation();
        if (mutation.isSource() || !collaborativeState.owner.equals(mutation.getContributor())) {
            String actor = mutation.isSource() ? "the declarative source" : "contributor '" + mutation.getContributor() + "'";
            throw new IllegalStateException("Cannot set the convention of collaborative property '" + getDisplayName().getDisplayName()
                + "' from " + actor + "; only owning contributor '" + collaborativeState.owner + "' may set its convention.");
        }
    }

    private void validateCollaborativeOrder() {
        if (collaborativeState != null) {
            collaborativeState.validateOrder();
        }
    }

    protected void assertCanMutate() {
        state.beforeMutate(this.getDisplayName());
    }

    @Nullable
    private Task getProducerTask() {
        if (producer == null) {
            return null;
        }
        Task task = producer.getTaskThatOwnsThisObject();
        if (task == null) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(getDisplayName().getCapitalizedDisplayName());
            formatter.append(" is declared as an output property of ");
            format(producer, formatter);
            formatter.append(" but does not have a task associated with it.");
            throw new IllegalStateException(formatter.toString());
        }
        return task;
    }

    private void format(ModelObject modelObject, TreeFormatter formatter) {
        if (modelObject.getModelIdentityDisplayName() != null) {
            formatter.append(modelObject.getModelIdentityDisplayName().getDisplayName());
            formatter.append(" (type ");
            formatter.appendType(modelObject.getClass());
            formatter.append(")");
        } else if (modelObject.hasUsefulDisplayName()) {
            formatter.append(modelObject.toString());
            formatter.append(" (type ");
            formatter.appendType(modelObject.getClass());
            formatter.append(")");
        } else {
            formatter.append("an object with type ");
            formatter.appendType(modelObject.getClass());
        }
    }

    @Contextual
    public static class PropertyQueryException extends RuntimeException {
        public PropertyQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Creates a shallow copy of this property. Further changes to this property (via {@code set(...)}, or {@code convention(Object...)}) do not
     * change the copy. However, the copy still reflects changes to the underlying providers that constitute this property. Consider the following snippet:
     * <pre>
     *     def upstream = objects.property(String).value("foo")
     *     def property = objects.property(String).value(upstream)
     *     def copy = property.shallowCopy()
     *     property.set("bar")  // does not affect contents of the copy
     *     upstream.set("qux")  // does affect the content of the copy
     *
     *     println(copy.get())  // prints qux
     * </pre>
     * <p>
     * The copy doesn't share the producer of this property, but inherits producers of the current property value.
     *
     * @return the shallow copy of this property
     */
    public ProviderInternal<T> shallowCopy() {
        return new ShallowCopyProvider();
    }

    /**
     * Replaces structurally visible reads of this property with a provider representing the
     * value immediately before the assignment. Providers hidden behind opaque computations are
     * deliberately not inspected.
     */
    protected <V> ProviderInternal<? extends V> substituteSelfReference(ProviderInternal<? extends V> provider) {
        return new ProviderSubstitution(this, this::previousValue).substitute(provider);
    }

    private ProviderInternal<T> previousValue() {
        if (isExplicit()) {
            return shallowCopy();
        }
        return new ConventionReadProvider();
    }

    private ProviderInternal<? extends T> snapshotCollaborativePipeline() {
        S source = value;
        ProviderSubstitution substitution = new ProviderSubstitution(
            collaborativeState.selectedSource,
            () -> new SupplierBackedProvider(source)
        );
        return substitution.substitute(collaborativeState.updatePipeline);
    }

    /**
     * A live read of the source selected by the ordinary explicit-versus-convention rule.
     */
    private class SelectedSourceProvider extends AbstractMinimalProvider<T> {
        @Override
        public ValueProducer getProducer() {
            try (EvaluationScopeContext ignored = openScope()) {
                return value.getProducer();
            }
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            try (EvaluationScopeContext context = openScope()) {
                return calculateOwnExecutionTimeValue(context, value);
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext context = openScope()) {
                return calculateValueFrom(context, value, consumer);
            }
        }

        @Override
        @Nullable
        public Class<T> getType() {
            return AbstractProperty.this.getType();
        }

        @Override
        protected String toStringNoReentrance() {
            return "selected-source(" + AbstractProperty.this.getDisplayName().getDisplayName() + ")";
        }
    }

    /**
     * Exposes an already validated property supplier as a provider pipeline node.
     */
    private class SupplierBackedProvider extends AbstractMinimalProvider<T> implements StructuralProvider<T> {
        private final S supplier;
        @Nullable
        private final ProviderInternal<? extends T> structuralSource;

        private SupplierBackedProvider(S supplier) {
            this.structuralSource = null;
            this.supplier = supplier;
        }

        private SupplierBackedProvider(ProviderInternal<? extends T> structuralSource) {
            this.structuralSource = structuralSource;
            this.supplier = supplierFromProvider(structuralSource);
        }

        @Override
        public ValueProducer getProducer() {
            return supplier.getProducer();
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            try (EvaluationScopeContext context = openScope()) {
                return calculateOwnExecutionTimeValue(context, supplier);
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext context = openScope()) {
                return calculateValueFrom(context, supplier, consumer);
            }
        }

        @Override
        @Nullable
        public Class<T> getType() {
            return AbstractProperty.this.getType();
        }

        @Override
        public ProviderInternal<T> substitute(ProviderSubstitution substitution) {
            if (structuralSource == null) {
                return this;
            }
            ProviderInternal<? extends T> substituted = substitution.substitute(structuralSource);
            if (substituted == structuralSource) {
                return this;
            }
            return new SupplierBackedProvider(substituted);
        }
    }

    private class CollaborativeState {
        private final String owner;
        private final List<String> globalOrder;
        private final SelectedSourceProvider selectedSource = new SelectedSourceProvider();
        private final List<CollaborationConstraint> constraints = new ArrayList<>();
        private final List<CollaborativeUpdate> updateTrace = new ArrayList<>();
        private ProviderInternal<? extends T> updatePipeline = selectedSource;
        private boolean validationDirty = true;

        private CollaborativeState(String owner, List<String> contributorOrder) {
            if (owner == null || owner.isEmpty()) {
                throw new IllegalArgumentException("A collaborative property owner must have a non-empty name.");
            }
            if (contributorOrder == null || contributorOrder.isEmpty()) {
                throw new IllegalArgumentException("A collaborative property must define at least one contributor.");
            }

            this.owner = owner;
            this.globalOrder = new ArrayList<>(contributorOrder.size());
            Set<String> uniqueContributors = new HashSet<>();
            for (String contributor : contributorOrder) {
                if (contributor == null || contributor.isEmpty()) {
                    throw new IllegalArgumentException("Collaborative property contributors must have non-empty names.");
                }
                if (!uniqueContributors.add(contributor)) {
                    throw new IllegalArgumentException("Collaborative property contributor '" + contributor + "' appears more than once in the global order.");
                }
                globalOrder.add(contributor);
            }
            if (!uniqueContributors.contains(owner)) {
                throw new IllegalArgumentException("Collaborative property owner '" + owner + "' is not present in the global contributor order.");
            }
        }

        private void acceptUpdate(CollaborativePropertyContext.Mutation mutation, ProviderInternal<? extends T> provider) {
            String contributor = mutation.getContributor();
            requireKnownContributor(contributor);

            String operation = operationKind(provider);
            if (operation == null) {
                throw new IllegalStateException("Cannot update collaborative property '" + getDisplayName().getDisplayName()
                    + "' from contributor '" + contributor + "': the assigned provider is not a supported structural self-update.");
            }

            ProviderSubstitution substitution = new ProviderSubstitution(AbstractProperty.this, () -> updatePipeline);
            ProviderInternal<? extends T> substituted = substitution.substitute(provider);
            if (!substitution.isTargetFound()) {
                throw new IllegalStateException("Cannot replace collaborative property '" + getDisplayName().getDisplayName()
                    + "' from contributor '" + contributor + "'; contributors may only apply structural self-updates.");
            }

            updatePipeline = new SupplierBackedProvider(substituted);
            updateTrace.add(new CollaborativeUpdate(contributor, operation, mutation.getOrigin()));
            validationDirty = true;
        }

        private void addConstraint(String before, String after) {
            requireKnownContributor(before);
            requireKnownContributor(after);
            CollaborationConstraint constraint = new CollaborationConstraint(before, after);
            if (!constraints.contains(constraint)) {
                constraints.add(constraint);
                validationDirty = true;
            }
        }

        private void requireKnownContributor(String contributor) {
            if (!globalOrder.contains(contributor)) {
                throw new IllegalArgumentException("Unknown contributor '" + contributor + "' for collaborative property '"
                    + getDisplayName().getDisplayName() + "'.");
            }
        }

        private void validateOrder() {
            if (!validationDirty) {
                return;
            }

            List<String> effectiveOrder = effectiveOrder();
            int previous = -1;
            for (CollaborativeUpdate update : updateTrace) {
                int current = effectiveOrder.indexOf(update.contributor);
                if (current < previous) {
                    throw invalidUpdateOrder(effectiveOrder);
                }
                previous = current;
            }
            validationDirty = false;
        }

        private List<String> effectiveOrder() {
            int contributorCount = globalOrder.size();
            boolean[][] edges = new boolean[contributorCount][contributorCount];
            int[] incoming = new int[contributorCount];
            for (CollaborationConstraint constraint : constraints) {
                int before = globalOrder.indexOf(constraint.before);
                int after = globalOrder.indexOf(constraint.after);
                if (!edges[before][after]) {
                    edges[before][after] = true;
                    incoming[after]++;
                }
            }

            PriorityQueue<Integer> available = new PriorityQueue<>();
            for (int i = 0; i < contributorCount; i++) {
                if (incoming[i] == 0) {
                    available.add(i);
                }
            }

            List<String> result = new ArrayList<>(contributorCount);
            while (!available.isEmpty()) {
                int next = available.remove();
                result.add(globalOrder.get(next));
                for (int successor = 0; successor < contributorCount; successor++) {
                    if (edges[next][successor] && --incoming[successor] == 0) {
                        available.add(successor);
                    }
                }
            }

            if (result.size() != contributorCount) {
                throw new IllegalStateException("Cannot observe collaborative property '" + getDisplayName().getDisplayName()
                    + "': local contributor ordering constraints contain a cycle.");
            }
            return result;
        }

        private IllegalStateException invalidUpdateOrder(List<String> effectiveOrder) {
            StringJoiner required = new StringJoiner(" < ");
            for (String contributor : effectiveOrder) {
                required.add(contributor);
            }

            StringJoiner recorded = new StringJoiner(" -> ");
            for (CollaborativeUpdate update : updateTrace) {
                recorded.add(update.contributor);
            }

            StringBuilder message = new StringBuilder()
                .append("Cannot observe collaborative property '")
                .append(getDisplayName().getDisplayName())
                .append("': contributor updates are out of order.\n\n")
                .append("Required contributor order:\n  ")
                .append(required)
                .append("\n\nRecorded update order:\n  ")
                .append(recorded);

            boolean hasOrigin = false;
            for (CollaborativeUpdate update : updateTrace) {
                hasOrigin |= update.origin != null;
            }
            if (hasOrigin) {
                message.append("\n\nRecorded updates:");
                for (CollaborativeUpdate update : updateTrace) {
                    message.append("\n  ").append(update.contributor).append(": ").append(update.operation);
                    if (update.origin != null) {
                        message.append(" at ").append(update.origin);
                    }
                }
            }
            return new IllegalStateException(message.toString());
        }

        @Nullable
        private String operationKind(ProviderInternal<? extends T> provider) {
            if (provider instanceof BiProvider) {
                return "Zip";
            }
            if (provider instanceof FlatMapProvider) {
                return "FlatMap";
            }
            if (provider instanceof TransformBackedProvider) {
                return "Map";
            }
            return null;
        }
    }

    private static class CollaborationConstraint {
        private final String before;
        private final String after;

        private CollaborationConstraint(String before, String after) {
            this.before = before;
            this.after = after;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CollaborationConstraint)) {
                return false;
            }
            CollaborationConstraint constraint = (CollaborationConstraint) other;
            return before.equals(constraint.before) && after.equals(constraint.after);
        }

        @Override
        public int hashCode() {
            return 31 * before.hashCode() + after.hashCode();
        }
    }

    private static class CollaborativeUpdate {
        private final String contributor;
        private final String operation;
        @Nullable
        private final String origin;

        private CollaborativeUpdate(String contributor, String operation, @Nullable String origin) {
            this.contributor = contributor;
            this.operation = operation;
            this.origin = origin;
        }
    }

    private class ShallowCopyProvider extends AbstractMinimalProvider<T> {
        // the value of "value" is immutable but the field is not, so copy it
        // (but use a different owner)
        private final S copiedValue = value;
        @Nullable
        private final ProviderInternal<? extends T> copiedCollaborativeValue = usesCollaborativePipeline()
            ? snapshotCollaborativePipeline()
            : null;

        @Override
        public ValueProducer getProducer() {
            try (EvaluationScopeContext ignored = openScope()) {
                if (copiedCollaborativeValue != null) {
                    return copiedCollaborativeValue.getProducer();
                }
                return copiedValue.getProducer();
            }
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            try (EvaluationScopeContext context = openScope()) {
                if (copiedCollaborativeValue != null) {
                    return copiedCollaborativeValue.calculateExecutionTimeValue();
                }
                return calculateOwnExecutionTimeValue(context, copiedValue);
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext context = openScope()) {
                if (copiedCollaborativeValue != null) {
                    return copiedCollaborativeValue.calculateValue(consumer);
                }
                return calculateValueFrom(context, copiedValue, consumer);
            }
        }

        @Override
        @Nullable
        public Class<T> getType() {
            return AbstractProperty.this.getType();
        }
    }

    /**
     * A live read of this property's convention which deliberately bypasses explicit-value
     * selection. This is used as the previous value when a property is self-assigned while it is
     * still implicit, so conventions installed or replaced later remain observable.
     */
    private class ConventionReadProvider extends AbstractMinimalProvider<T> {
        @Override
        public ValueProducer getProducer() {
            try (EvaluationScopeContext ignored = openScope()) {
                return getConventionSupplier().getProducer();
            }
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            try (EvaluationScopeContext context = openScope()) {
                return calculateOwnExecutionTimeValue(context, getConventionSupplier());
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext context = openScope()) {
                return calculateValueFrom(context, getConventionSupplier(), consumer);
            }
        }

        @Override
        @Nullable
        public Class<T> getType() {
            return AbstractProperty.this.getType();
        }

        @Override
        protected String toStringNoReentrance() {
            return "convention(" + AbstractProperty.this.getDisplayName().getDisplayName() + ")";
        }
    }
}
