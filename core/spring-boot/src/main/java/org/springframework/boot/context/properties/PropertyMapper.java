/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot.context.properties;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

/**
 * Utility that can be used to map values from a supplied source to a destination.
 * Primarily intended to be help when mapping from
 * {@link ConfigurationProperties @ConfigurationProperties} to third-party classes.
 * <p>
 * Can filter values based on predicates and adapt values if needed. For example:
 * <pre class="code">
 * PropertyMapper map = PropertyMapper.get();
 * map.from(source::getName)
 *   .to(destination::setName);
 * map.from(source::getTimeout)
 *   .whenNonNull()
 *   .asInt(Duration::getSeconds)
 *   .to(destination::setTimeoutSecs);
 * map.from(source::isEnabled)
 *   .whenFalse().
 *   .toCall(destination::disable);
 * </pre>
 * <p>
 * Mappings can ultimately be applied to a {@link Source#to(Consumer) setter}, trigger a
 * {@link Source#toCall(Runnable) method call} or create a
 * {@link Source#toInstance(Function) new instance}.
 *
 * @author Phillip Webb
 * @author Artsiom Yudovin
 * @author Chris Bono
 * @since 2.0.0
 */
public final class PropertyMapper {

	private static final Predicate<?> ALWAYS = (t) -> true;

	private static final PropertyMapper INSTANCE = new PropertyMapper(null, null);

	private final @Nullable PropertyMapper parent;

	private final @Nullable SourceOperator sourceOperator;

	private PropertyMapper(@Nullable PropertyMapper parent, @Nullable SourceOperator sourceOperator) {
		this.parent = parent;
		this.sourceOperator = sourceOperator;
	}

	/**
	 * Return a new {@link PropertyMapper} instance that applies
	 * {@link Source#whenNonNull() whenNonNull} to every source.
	 * @return a new property mapper instance
	 */
	public PropertyMapper alwaysApplyingWhenNonNull() {
		return alwaysApplying(this::whenNonNull);
	}

	private <T> Source<T> whenNonNull(Source<T> source) {
		return source.whenNonNull();
	}

	/**
	 * Return a new {@link PropertyMapper} instance that applies the given
	 * {@link SourceOperator} to every source.
	 * @param operator the source operator to apply
	 * @return a new property mapper instance
	 */
	public PropertyMapper alwaysApplying(SourceOperator operator) {
		Assert.notNull(operator, "'operator' must not be null");
		return new PropertyMapper(this, operator);
	}

	/**
	 * Return a new {@link Source} from the specified value supplier that can be used to
	 * perform the mapping.
	 * @param <T> the source type
	 * @param supplier the value supplier
	 * @return a {@link Source} that can be used to complete the mapping
	 * @see #from(Object)
	 */
	public <T> Source<T> from(Supplier<T> supplier) {
		Assert.notNull(supplier, "'supplier' must not be null");
		Source<T> source = getSource(supplier);
		if (this.sourceOperator != null) {
			source = this.sourceOperator.apply(source);
		}
		return source;
	}

	/**
	 * Return a new {@link Source} from the specified value that can be used to perform
	 * the mapping.
	 * @param <T> the source type
	 * @param value the value
	 * @return a {@link Source} that can be used to complete the mapping
	 */
	public <T> Source<T> from(@Nullable T value) {
		return from(() -> value);
	}

	@SuppressWarnings("unchecked")
	private <T> Source<T> getSource(Supplier<T> supplier) {
		if (this.parent != null) {
			return this.parent.from(supplier);
		}
		return new Source<>(SingletonSupplier.of(supplier), (Predicate<T>) ALWAYS);
	}

	/**
	 * Return the property mapper.
	 * @return the property mapper
	 */
	public static PropertyMapper get() {
		return INSTANCE;
	}

	/**
	 * An operation that can be applied to a {@link Source}.
	 */
	@FunctionalInterface
	public interface SourceOperator {

		/**
		 * Apply the operation to the given source.
		 * @param <T> the source type
		 * @param source the source to operate on
		 * @return the updated source
		 */
		<T> Source<T> apply(Source<T> source);

	}

	/**
	 * A source that is in the process of being mapped.
	 *
	 * @param <T> the source type
	 */
	public static final class Source<T> {

		private final Supplier<T> supplier;

		private final Predicate<T> predicate;

		private Source(Supplier<T> supplier, Predicate<T> predicate) {
			Assert.notNull(predicate, "'predicate' must not be null");
			this.supplier = supplier;
			this.predicate = predicate;
		}

		/**
		 * Return an adapted version of the source with {@link Integer} type.
		 * @param <R> the resulting type
		 * @param adapter an adapter to convert the current value to a number.
		 * @return a new adapted source instance
		 */
		public <R extends Number> Source<Integer> asInt(Function<T, R> adapter) {
			return as(adapter).as(Number::intValue);
		}

		/**
		 * Return an adapted version of the source changed through the given adapter
		 * function.
		 * @param <R> the resulting type
		 * @param adapter the adapter to apply
		 * @return a new adapted source instance
		 */
		public <R> Source<R> as(Function<T, R> adapter) {
			Assert.notNull(adapter, "'adapter' must not be null");
			Supplier<Boolean> test = () -> this.predicate.test(this.supplier.get());
			Predicate<R> predicate = (t) -> test.get();
			Supplier<R> supplier = () -> {
				if (test.get()) {
					return adapter.apply(this.supplier.get());
				}
				return null;
			};
			return new Source<>(supplier, predicate);
		}

		/**
		 * Return a filtered version of the source that won't map non-null values or
		 * suppliers that throw a {@link NullPointerException}.
		 * @return a new filtered source instance
		 */
		public Source<T> whenNonNull() {
			return new Source<>(new NullPointerExceptionSafeSupplier<>(this.supplier), Objects::nonNull);
		}

		/**
		 * Return a filtered version of the source that will only map values that are
		 * {@code true}.
		 * @return a new filtered source instance
		 */
		public Source<T> whenTrue() {
			return when(Boolean.TRUE::equals);
		}

		/**
		 * Return a filtered version of the source that will only map values that are
		 * {@code false}.
		 * @return a new filtered source instance
		 */
		public Source<T> whenFalse() {
			return when(Boolean.FALSE::equals);
		}

		/**
		 * Return a filtered version of the source that will only map values that have a
		 * {@code toString()} containing actual text.
		 * @return a new filtered source instance
		 */
		public Source<T> whenHasText() {
			return when((value) -> StringUtils.hasText(Objects.toString(value, null)));
		}

		/**
		 * Return a filtered version of the source that will only map values equal to the
		 * specified {@code object}.
		 * @param object the object to match
		 * @return a new filtered source instance
		 */
		public Source<T> whenEqualTo(Object object) {
			return when(object::equals);
		}

		/**
		 * Return a filtered version of the source that will only map values that are an
		 * instance of the given type.
		 * @param <R> the target type
		 * @param target the target type to match
		 * @return a new filtered source instance
		 */
		public <R extends T> Source<R> whenInstanceOf(Class<R> target) {
			return when(target::isInstance).as(target::cast);
		}

		/**
		 * Return a filtered version of the source that won't map values that match the
		 * given predicate.
		 * @param predicate the predicate used to filter values
		 * @return a new filtered source instance
		 */
		public Source<T> whenNot(Predicate<T> predicate) {
			Assert.notNull(predicate, "'predicate' must not be null");
			return when(predicate.negate());
		}

		/**
		 * Return a filtered version of the source that won't map values that don't match
		 * the given predicate.
		 * @param predicate the predicate used to filter values
		 * @return a new filtered source instance
		 */
		public Source<T> when(Predicate<T> predicate) {
			Assert.notNull(predicate, "'predicate' must not be null");
			return new Source<>(this.supplier, (this.predicate != null) ? this.predicate.and(predicate) : predicate);
		}

		/**
		 * Complete the mapping by passing any non-filtered value to the specified
		 * consumer. The method is designed to be used with mutable objects.
		 * @param consumer the consumer that should accept the value if it's not been
		 * filtered
		 */
		public void to(Consumer<T> consumer) {
			Assert.notNull(consumer, "'consumer' must not be null");
			T value = this.supplier.get();
			if (this.predicate.test(value)) {
				consumer.accept(value);
			}
		}

		/**
		 * Complete the mapping for any non-filtered value by applying the given function
		 * to an existing instance and returning a new one. For filtered values, the
		 * {@code instance} parameter is returned unchanged. The method is designed to be
		 * used with immutable objects.
		 * @param <R> the result type
		 * @param instance the current instance
		 * @param mapper the mapping function
		 * @return a new mapped instance or the original instance
		 * @since 3.0.0
		 */
		public <R> R to(R instance, BiFunction<R, T, R> mapper) {
			Assert.notNull(instance, "'instance' must not be null");
			Assert.notNull(mapper, "'mapper' must not be null");
			T value = this.supplier.get();
			return (!this.predicate.test(value)) ? instance : mapper.apply(instance, value);
		}

		/**
		 * Complete the mapping by creating a new instance from the non-filtered value.
		 * @param <R> the resulting type
		 * @param factory the factory used to create the instance
		 * @return the instance
		 * @throws NoSuchElementException if the value has been filtered
		 */
		public <R> R toInstance(Function<T, R> factory) {
			Assert.notNull(factory, "'factory' must not be null");
			T value = this.supplier.get();
			if (!this.predicate.test(value)) {
				throw new NoSuchElementException("No value present");
			}
			return factory.apply(value);
		}

		/**
		 * Complete the mapping by calling the specified method when the value has not
		 * been filtered.
		 * @param runnable the method to call if the value has not been filtered
		 */
		public void toCall(Runnable runnable) {
			Assert.notNull(runnable, "'runnable' must not be null");
			T value = this.supplier.get();
			if (this.predicate.test(value)) {
				runnable.run();
			}
		}

	}

	/**
	 * Supplier that will catch and ignore any {@link NullPointerException}.
	 */
	private static class NullPointerExceptionSafeSupplier<T> implements Supplier<T> {

		private final Supplier<T> supplier;

		NullPointerExceptionSafeSupplier(Supplier<T> supplier) {
			this.supplier = supplier;
		}

		@Override
		public @Nullable T get() {
			try {
				return this.supplier.get();
			}
			catch (NullPointerException ex) {
				return null;
			}
		}

	}

}
