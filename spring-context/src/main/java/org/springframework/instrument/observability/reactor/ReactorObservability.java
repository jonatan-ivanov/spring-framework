/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.instrument.observability.reactor;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Reactive Span pointcuts factories.
 *
 * @author Stephane Maldini
 * @author Roman Matiushchenko
 * @since 2.0.0
 */
// TODO: this is public as it is used out of package, but unlikely intended to be
// non-internal
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class ReactorObservability {

	private static final Log log = LogFactory.getLog(ReactorObservability.class);

	private ReactorObservability() {
	}

	/**
	 * Function that does additional wrapping of the Reactor context. I guess we
	 * will need a list of functions instead of a single one.
	 */
	public static Function<Context, Context> contextWrappingFunction = Function.identity();

	/**
	 * Return a span operator pointcut given a Tracing. This can be used in reactor via
	 * {@link Flux#transform(Function)},
	 * {@link Mono#transform(Function)},
	 * {@link Hooks#onLastOperator(Function)} or
	 * {@link Hooks#onLastOperator(Function)}. The Span operator
	 * pointcut will pass the Scope of the Span without ever creating any new spans.
	 * @param springContext the Spring context.
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 * @return a new lazy span operator pointcut
	 */
	// Much of Boot assumes that the Spring context will be a
	// ConfigurableApplicationContext, rooted in SpringApplication's
	// requirement for it to be so. Previous versions of Reactor
	// instrumentation injected both BeanFactory and also
	// ConfigurableApplicationContext. This chooses the more narrow
	// signature as it is simpler than explaining instanceof checks.
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> scopePassingRecordingOperator(
			ConfigurableApplicationContext springContext) {
		if (log.isTraceEnabled()) {
			log.trace("Scope passing operator [" + springContext + "]");
		}

		// keep a reference outside the lambda so that any caching will be visible to
		// all publishers
		LazyBean<MeterRegistry> lazyRecorder = LazyBean.create(springContext, MeterRegistry.class);

		return Operators.liftPublisher(p -> !(p instanceof Fuseable.ScalarCallable),
				(BiFunction) liftFunction(springContext, lazyRecorder));
	}

	/**
	 * Creates scope passing span operator which applies only to not
	 * {@code Scannable.Attr.RunStyle.SYNC} {@code Publisher}s. Used by
	 * {@code InstrumentationType#DECORATE_ON_EACH}
	 * @param springContext the Spring context.
	 * @param <T> an arbitrary type that is left unchanged by the span operator.
	 * @return operator to apply to {@link Hooks#onEachOperator(Function)}.
	 */
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> onEachOperatorForOnEachInstrumentation(
			ConfigurableApplicationContext springContext) {
		if (log.isTraceEnabled()) {
			log.trace("Scope passing operator [" + springContext + "]");
		}

		// keep a reference outside the lambda so that any caching will be visible to
		// all publishers
		@SuppressWarnings("rawtypes")
		LazyBean<MeterRegistry> lazyRecorder = LazyBean.create(springContext,
				MeterRegistry.class);

		@SuppressWarnings("rawtypes")
		Predicate<Publisher> shouldDecorate = ReactorHooksHelper::shouldDecorate;
		@SuppressWarnings("rawtypes")
		BiFunction<Publisher, ? super CoreSubscriber<? super T>, ? extends CoreSubscriber<? super T>> lifter = liftFunction(
				springContext, lazyRecorder);

		return Operators.liftPublisher(shouldDecorate, ReactorHooksHelper.named(ReactorHooksHelper.LIFTER_NAME, lifter));
	}

	@SuppressWarnings("rawtypes")
	static <O> BiFunction<Publisher, ? super CoreSubscriber<? super O>, ? extends CoreSubscriber<? super O>> liftFunction(
			ConfigurableApplicationContext springContext,
			LazyBean<MeterRegistry> lazyRecorder) {
		return (p, sub) -> {
			if (!springContext.isActive() || !springContext.isRunning()) {
				if (log.isTraceEnabled()) {
					String message = "Spring Context [" + springContext
							+ "] is not yet refreshed. This is unexpected. Reactor Context is [" + context(sub)
							+ "] and name is [" + name(sub) + "]";
					log.trace(message);
				}
				return sub;
			}

			Context context = context(sub);

			if (log.isTraceEnabled()) {
				log.trace("Spring context [" + springContext + "], Reactor context [" + context + "], name ["
						+ name(sub) + "]");
			}

			// Try to get the current trace context bean, lenient when there are problems
			MeterRegistry recorder = lazyRecorder.get();
			if (recorder == null) {
				if (log.isTraceEnabled()) {
					String message = "Spring Context [" + springContext
							+ "] did not return a Recorder . Reactor Context is ["
							+ context
							+ "] and name is [" + name(sub) + "]";
					log.trace(message);
				}
				return sub;
			}

			Timer.Sample parentRecording = recorder.getCurrentSample();

			if (parentRecording == null) {
				return sub; // no need to scope a null parent
			}

			// Handle scenarios such as Mono.defer
			if (sub instanceof ScopePassingRecordingSubscriber) {
				ScopePassingRecordingSubscriber<?> scopePassing = (ScopePassingRecordingSubscriber<?>) sub;
				if (scopePassing.parent.equals(parentRecording)) {
					return sub; // don't double-wrap
				}
			}

			context = contextWithBeans(context, lazyRecorder);
			if (log.isTraceEnabled()) {
				log.trace("Spring context [" + springContext + "], Reactor context [" + context + "], name ["
						+ name(sub) + "]");
			}

			if (log.isTraceEnabled()) {
				log.trace("Creating a scope passing span subscriber with Reactor Context " + "[" + context
						+ "] and name [" + name(sub) + "]");
			}

			return new ScopePassingRecordingSubscriber<>(sub, context, parentRecording);
		};
	}

	@SuppressWarnings("rawtypes")
	private static <T> Context contextWithBeans(Context context,
			LazyBean<MeterRegistry> recorder) {
		if (!context.hasKey(MeterRegistry.class)) {
			context = context.put(MeterRegistry.class, recorder.getOrError());
		}
		return context;
	}

	/**
	 * Creates a context with beans in it.
	 * @param springContext spring context
	 * @param <T> an arbitrary type that is left unchanged by the span operator
	 * @return a new operator pointcut that has beans in the context
	 */
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> springContextSpanOperator(
			ConfigurableApplicationContext springContext) {
		if (log.isTraceEnabled()) {
			log.trace("Spring Context passing operator [" + springContext + "]");
		}

		LazyBean<MeterRegistry> lazyRecorder = LazyBean.create(springContext,
				MeterRegistry.class);

		return Operators.liftPublisher(p -> {
			// We don't scope scalar results as they happen in an instant. This prevents
			// excessive overhead when using Flux/Mono #just, #empty, #error, etc.
			return !(p instanceof Fuseable.ScalarCallable) && springContext.isActive();
		}, (p, sub) -> {
			Context ctxBefore = context(sub);
			Context context = contextWithBeans(ctxBefore, lazyRecorder);
			if (context == ctxBefore) {
				return sub;
			}
			return new SleuthContextOperator<>(context, sub);
		});
	}

	/**
	 * Creates tracing context capturing reactor operator. Used by
	 * {@code InstrumentationType#DECORATE_ON_EACH}.
	 * @param springContext the Spring context.
	 * @param <T> an arbitrary type that is left unchanged by the span operator.
	 * @return operator to apply to {@link Hooks#onLastOperator(Function)} for
	 * {@code InstrumentationType#DECORATE_ON_EACH}
	 */
	public static <T> Function<? super Publisher<T>, ? extends Publisher<T>> onLastOperatorForOnEachInstrumentation(
			ConfigurableApplicationContext springContext) {
		@SuppressWarnings("rawtypes")
		LazyBean<MeterRegistry> lazyRecorder = LazyBean.create(springContext, MeterRegistry.class);

		BiFunction<Publisher, ? super CoreSubscriber<? super T>, ? extends CoreSubscriber<? super T>> scopePassingSpanSubscriber = liftFunction(
				springContext, lazyRecorder);

		BiFunction<Publisher, ? super CoreSubscriber<? super T>, ? extends CoreSubscriber<? super T>> skipIfNoTraceCtx = (
				pub, sub) -> {
			// lazyCurrentTraceContext.get() is not null here. see predicate bellow
			MeterRegistry recorder = lazyRecorder.get();
			if (context(sub).getOrDefault(MeterRegistry.class, null) == recorder) {
				return sub;
			}
			return scopePassingSpanSubscriber.apply(pub, sub);
		};

		return Operators.liftPublisher(p -> {
			/*
			 * this prevent double decoration when last operator in the chain is not SYNC
			 * like {@code Mono.fromSuppler(() -> ...).subscribeOn(Schedulers.parallel())}
			 */
			if (ReactorHooksHelper.isRecordingPropagator(p)) {
				return false;
			}
			boolean addRecording = !(p instanceof Fuseable.ScalarCallable) && springContext.isActive();
			if (addRecording) {
				@SuppressWarnings("rawtypes")
				MeterRegistry recorder = lazyRecorder.get();
				if (recorder != null) {
					addRecording = recorder.getCurrentSample() != null;
				}
			}
			return addRecording;
		}, ReactorHooksHelper.named(ReactorHooksHelper.LIFTER_NAME, skipIfNoTraceCtx));
	}

	private static <T> Context context(CoreSubscriber<? super T> sub) {
		try {
			return sub.currentContext();
		}
		catch (Exception ex) {
			if (log.isDebugEnabled()) {
				log.debug("Exception occurred while trying to retrieve the context", ex);
			}
		}
		return Context.empty();
	}

	static String name(CoreSubscriber<?> sub) {
		return Scannable.from(sub).name();
	}

	static Timer.Sample traceContext(Context context, MeterRegistry registry) {
		if (context.hasKey(Timer.Sample.class)) {
			return context.get(Timer.Sample.class);
		}
		return registry.getCurrentSample();
	}

	public static Function<Runnable, Runnable> scopePassingOnScheduleHook(
			ConfigurableApplicationContext springContext) {
		LazyBean<MeterRegistry> lazyCurrentTraceContext = LazyBean.create(springContext,
				MeterRegistry.class);
		return delegate -> {
			if (springContext.isActive()) {
				final MeterRegistry currentTraceContext = lazyCurrentTraceContext.get();
				if (currentTraceContext == null) {
					return delegate;
				}
				final Timer.Sample sample = currentTraceContext.getCurrentSample();
				return () -> {
					sample.restore();
//					try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(traceContext)) {
						delegate.run();
//					}
				};
			}
			return delegate;
		};
	}

	/**
	 * Wraps the given Mono in a trace representation. Retrieves the span from
	 * context, creates a child span with the given name.
	 * @param recorder - recorder bean
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Mono} to be wrapped in tracing
	 * @param <T> - type returned by the Mono
	 * @param spanCustomizer - customizer for the child span
	 * @return traced Mono
	 */
	public static <T> Mono<T> mono(@NonNull MeterRegistry recorder,
			@NonNull String childSpanName, @NonNull Supplier<Mono<T>> supplier,
			@NonNull BiConsumer<T, Timer.Sample> spanCustomizer) {
		return runMonoSupplierInScope(supplier, spanCustomizer).contextWrite(
				context -> ReactorObservability.enhanceContext(recorder, context,
						childSpanName));
	}

	/**
	 * Wraps the given Mono in a trace representation. Retrieves the span from
	 * context, creates a child span with the given name.
	 * @param recorder - recorder bean
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Mono} to be wrapped in tracing
	 * @param <T> - type returned by the Mono
	 * @param spanCustomizer - customizer for the child span
	 * @param spanFunction - function that creates a new or child span
	 * @return traced Mono
	 */
	public static <T> Mono<T> mono(@NonNull MeterRegistry recorder,
			@NonNull String childSpanName, @NonNull Supplier<Mono<T>> supplier,
			@NonNull BiConsumer<T, Timer.Sample> spanCustomizer,
			@NonNull Function<Timer.Sample, Timer.Sample> spanFunction) {
		return runMonoSupplierInScope(supplier, spanCustomizer).contextWrite(context -> ReactorObservability
				.enhanceContext(recorder, context, childSpanName));
	}

	private static <T> Mono<T> runMonoSupplierInScope(Supplier<Mono<T>> supplier,
			BiConsumer<T, Timer.Sample> customizer) {
		return Mono.deferContextual(contextView -> {
			MeterRegistry recorder = contextView.get(MeterRegistry.class);
			Timer.Sample recording = recorder.getCurrentSample();
			// @formatter:off
			return supplier.get()
					.map(t -> {
						customizer.accept(t, recording);
						return t;
					})
					// TODO: Fix me when this is resolved in Reactor
//					.doOnSubscribe(__ -> scope.close())
					.doOnError(recording::error)
					.doFinally(signalType -> recording.stop(recorder.timer("reactive")));
			// @formatter:on
		});
	}

	/**
	 * Wraps the given Mono in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Mono} to be wrapped in tracing
	 * @param <T> - type returned by the Mono
	 * @return traced Mono
	 */
	public static <T> Mono<T> mono(@NonNull MeterRegistry recorder,
			@NonNull String childSpanName, @NonNull Supplier<Mono<T>> supplier) {
		return mono(recorder, childSpanName, supplier, (o, span) -> {
		});
	}

	/**
	 * Wraps the given Mono in a trace representation. Puts the provided span to context.
	 * @param supplier - supplier of a {@link Mono} to be wrapped in tracing
	 * @param <T> - type returned by the Mono
	 * @return traced Mono
	 */
	public static <T> Mono<T> mono(
			@NonNull Timer.Sample recording,
			@NonNull Supplier<Mono<T>> supplier) {
		return runMonoSupplierInScope(supplier, (o, span1) -> {
		}).contextWrite(context -> ReactorObservability.makeRecordingCurrent(recording, context));
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @param spanCustomizer - customizer for the child span
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(MeterRegistry registry,
			@NonNull String childSpanName, @NonNull Supplier<Flux<T>> supplier,
			@NonNull BiConsumer<T, Timer.Sample> spanCustomizer) {
		return runFluxSupplierInScope(supplier, spanCustomizer).contextWrite(
				context -> ReactorObservability.enhanceContext(registry, context, childSpanName));
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @param spanCustomizer - customizer for the child span
	 * @param spanFunction - function that creates a new or child span
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(
			@NonNull String childSpanName, @NonNull Supplier<Flux<T>> supplier,
			@NonNull BiConsumer<T, Timer.Sample> spanCustomizer, @NonNull Function<Timer.Sample, Timer.Sample> spanFunction) {
		return runFluxSupplierInScope(supplier, spanCustomizer).contextWrite(context -> ReactorObservability
				.enhanceContext(context, childSpanName, spanFunction));
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param span - span to put in context
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(@NonNull Timer.Sample span,
			@NonNull Supplier<Flux<T>> supplier) {
		return runFluxSupplierInScope(supplier, (o, span1) -> {
		}).contextWrite(context -> ReactorObservability.makeRecordingCurrent(span, context));
	}

	private static <T> Flux<T> runFluxSupplierInScope(Supplier<Flux<T>> supplier, BiConsumer<T, Timer.Sample> spanCustomizer) {
		return Flux.deferContextual(contextView -> {
			Timer.Sample span = contextView.get(Timer.Sample.class);
			MeterRegistry registry = contextView.get(MeterRegistry.class);
			// @formatter:off
			return supplier.get()
					.map(t -> {
						spanCustomizer.accept(t, span);
						return t;
					})
					// TODO: Fix me when this is resolved in Reactor
//					.doOnSubscribe(__ -> scope.close())
					.doOnError(span::error)
					.doFinally(signalType -> {
						span.stop(registry.timer("reactive"));
					});
			// @formatter:on
		});
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(MeterRegistry registry, @NonNull String childSpanName, @NonNull Supplier<Flux<T>> supplier) {
		return tracedFlux(registry, childSpanName, supplier, (o, span) -> {
		});
	}

	private static Timer.Sample childSpanFromContext(MeterRegistry registry, Context context, String childSpanName) {
		return childSpanFromContext(context, childSpanName, span -> restore(registry, span));
	}

	private static Timer.Sample restore(MeterRegistry registry, Timer.Sample sample) {
		if (sample != null) {
			sample.restore();
		}

		return Timer.start(registry);
	}

	private static Timer.Sample childSpanFromContext(Context context, String childSpanName, Function<Timer.Sample, Timer.Sample> spanSupplier) {
		Timer.Sample span = context.getOrDefault(Timer.Sample.class, null);
		if (span == null) {
			span = spanSupplier.apply(null);
			if (log.isDebugEnabled()) {
				log.debug("There was no previous span in reactor context, created a new one [" + span + "]");
			}
		}
		else {
			if (log.isDebugEnabled()) {
				log.debug("Found a span in reactor context [" + span + "]");
			}
			span = spanSupplier.apply(span);
			if (log.isDebugEnabled()) {
				log.debug("Created a child span [" + span + "]");
			}
		}
//		return span.name(childSpanName).start();
		return span;
	}

	/**
	 * Updates the Reactor context with tracing information. Creates a new span if there
	 * is no current span. Creates a child span if there was an entry in the context
	 * already.
	 * @param context Reactor context
	 * @param childSpanName child span name when there is no span in context
	 * @param spanSupplier function that creates a new or child span
	 * @return updated Reactor context
	 */
	public static Context enhanceContext(
			Context context, String childSpanName, Function<Timer.Sample, Timer.Sample> spanSupplier) {
		Timer.Sample span = childSpanFromContext(context, childSpanName, spanSupplier);
		return makeRecordingCurrent(span, context);
	}

	/**
	 * Updates the Reactor context with tracing information. Creates a new span if there
	 * is no current span. Creates a child span if there was an entry in the context
	 * already.
	 * @param context Reactor context
	 * @param childSpanName child span name when there is no span in context
	 * @return updated Reactor context
	 */
	public static Context enhanceContext(MeterRegistry recorder, Context context,
			String childSpanName) {
		Timer.Sample span = childSpanFromContext(recorder, context, childSpanName);
		return makeRecordingCurrent(span, context);
	}

	/**
	 * Puts the provided span in scope and in Reactor context.
	 * @param context Reactor context
	 * @return mutated context
	 */
	public static Context makeRecordingCurrent(Timer.Sample recording, Context context) {
		Context newContext = context.put(Timer.Sample.class, recording.restore());
		return wrapContext(newContext);
	}

	/**
	 * Mutates the Reactor context depending on the classpath contents.
	 * @param context Reactor context
	 * @return mutated context
	 */
	public static Context wrapContext(Context context) {
		return contextWrappingFunction.apply(context);
	}

	/**
	 * Retrieves span from Reactor context.
	 * @param context context view
	 * @return span from Reactor context or creates a new one if missing
	 */
	public static Timer.Sample spanFromContext(MeterRegistry registry, ContextView context) {
		Timer.Sample span = context.getOrDefault(Timer.Sample.class, null);
		if (span != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a span in reactor context [" + span + "]");
			}
			return span;
		}
		span.restore(); // TODO: Is this needed?
		Timer.Sample newSpan = Timer.start(registry);
		if (log.isDebugEnabled()) {
			log.debug("No span was found - will create a new one [" + newSpan + "]");
		}
		return newSpan;
	}

}

@SuppressWarnings("rawtypes")
class SleuthContextOperator<T> implements Subscription, CoreSubscriber<T>, Scannable {

	private final Context context;

	private final Subscriber<? super T> subscriber;

	private Subscription s;

	SleuthContextOperator(Context context, Subscriber<? super T> subscriber) {
		this.context = context;
		this.subscriber = subscriber;
	}

	@Override
	public void onSubscribe(Subscription subscription) {
		this.s = subscription;
		this.subscriber.onSubscribe(this);
	}

	@Override
	public void request(long n) {
		this.s.request(n);
	}

	@Override
	public void cancel() {
		this.s.cancel();
	}

	@Override
	public void onNext(T o) {
		this.subscriber.onNext(o);
	}

	@Override
	public void onError(Throwable throwable) {
		this.subscriber.onError(throwable);
	}

	@Override
	public void onComplete() {
		this.subscriber.onComplete();
	}

	@Override
	public Context currentContext() {
		return this.context;
	}

	@Nullable
	@Override
	public Object scanUnsafe(Attr key) {
		if (key == Attr.RUN_STYLE) {
			return Attr.RunStyle.SYNC;
		}
		return null;
	}

}
