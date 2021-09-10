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
import org.springframework.core.observability.event.Recorder;
import org.springframework.core.observability.event.interval.IntervalRecording;
import org.springframework.core.observability.tracing.CurrentTraceContext;
import org.springframework.core.observability.tracing.Span;
import org.springframework.core.observability.tracing.TraceContext;
import org.springframework.lang.NonNull;

/**
 * Reactive Span pointcuts factories.
 *
 * @author Stephane Maldini
 * @author Roman Matiushchenko
 * @since 2.0.0
 */
// TODO: this is public as it is used out of package, but unlikely intended to be
// non-internal
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
		LazyBean<Recorder> lazyRecorder = LazyBean.create(springContext,
				Recorder.class);

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
		LazyBean<Recorder> lazyRecorder = LazyBean.create(springContext,
				Recorder.class);

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
			LazyBean<Recorder> lazyRecorder) {
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
			Recorder<?> recorder = lazyRecorder.get();
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

			IntervalRecording<?> parentRecording = recorder.getCurrentRecording();

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
			LazyBean<Recorder> recorder) {
		if (!context.hasKey(Recorder.class)) {
			context = context.put(Recorder.class, recorder.getOrError());
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

		LazyBean<Recorder> lazyRecorder = LazyBean.create(springContext,
				Recorder.class);

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
		LazyBean<Recorder> lazyRecorder = LazyBean.create(springContext, Recorder.class);

		BiFunction<Publisher, ? super CoreSubscriber<? super T>, ? extends CoreSubscriber<? super T>> scopePassingSpanSubscriber = liftFunction(
				springContext, lazyRecorder);

		BiFunction<Publisher, ? super CoreSubscriber<? super T>, ? extends CoreSubscriber<? super T>> skipIfNoTraceCtx = (
				pub, sub) -> {
			// lazyCurrentTraceContext.get() is not null here. see predicate bellow
			Recorder recorder = lazyRecorder.get();
			if (context(sub).getOrDefault(Recorder.class, null) == recorder) {
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
				Recorder recorder = lazyRecorder.get();
				if (recorder != null) {
					addRecording = recorder.getCurrentRecording() != null;
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

	/**
	 * Like {@link CurrentTraceContext#context()}, except it first checks the reactor
	 * context.
	 */
	static TraceContext traceContext(Context context, CurrentTraceContext fallback) {
		if (context.hasKey(TraceContext.class)) {
			return context.get(TraceContext.class);
		}
		return fallback.context();
	}

	public static Function<Runnable, Runnable> scopePassingOnScheduleHook(
			ConfigurableApplicationContext springContext) {
		LazyBean<CurrentTraceContext> lazyCurrentTraceContext = LazyBean.create(springContext,
				CurrentTraceContext.class);
		return delegate -> {
			if (springContext.isActive()) {
				final CurrentTraceContext currentTraceContext = lazyCurrentTraceContext.get();
				if (currentTraceContext == null) {
					return delegate;
				}
				final TraceContext traceContext = currentTraceContext.context();
				return () -> {
					try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(traceContext)) {
						delegate.run();
					}
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
	public static <T> Mono<T> mono(@NonNull Recorder<?> recorder,
			@NonNull String childSpanName, @NonNull Supplier<Mono<T>> supplier,
			@NonNull BiConsumer<T, IntervalRecording<?>> spanCustomizer) {
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
	public static <T> Mono<T> mono(@NonNull Recorder<?> recorder,
			@NonNull String childSpanName, @NonNull Supplier<Mono<T>> supplier,
			@NonNull BiConsumer<T, IntervalRecording<?>> spanCustomizer,
			@NonNull Function<IntervalRecording<?>, IntervalRecording<?>> spanFunction) {
		return runMonoSupplierInScope(supplier, spanCustomizer).contextWrite(context -> ReactorObservability
				.enhanceContext(recorder, context, childSpanName));
	}

	private static <T> Mono<T> runMonoSupplierInScope(Supplier<Mono<T>> supplier,
			BiConsumer<T, IntervalRecording<?>> customizer) {
		return Mono.deferContextual(contextView -> {
			Recorder<?> recorder = contextView.get(Recorder.class);
			IntervalRecording<?> recording = recorder.getCurrentRecording();
			// @formatter:off
			return supplier.get()
					.map(t -> {
						customizer.accept(t, recording);
						return t;
					})
					// TODO: Fix me when this is resolved in Reactor
//					.doOnSubscribe(__ -> scope.close())
					.doOnError(recording::error)
					.doFinally(signalType -> recording.close());
			// @formatter:on
		});
	}

	/**
	 * Wraps the given Mono in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param tracer - Tracer bean
	 * @param currentTraceContext - CurrentTraceContext bean
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Mono} to be wrapped in tracing
	 * @param <T> - type returned by the Mono
	 * @return traced Mono
	 */
	public static <T> Mono<T> mono(@NonNull Recorder<?> recorder,
			@NonNull String childSpanName, @NonNull Supplier<Mono<T>> supplier) {
		return mono(recorder, childSpanName, supplier, (o, span) -> {
		});
	}

	/**
	 * Wraps the given Mono in a trace representation. Puts the provided span to context.
	 * @param tracer - Tracer bean
	 * @param span - span to put in context
	 * @param supplier - supplier of a {@link Mono} to be wrapped in tracing
	 * @param <T> - type returned by the Mono
	 * @return traced Mono
	 */
	public static <T> Mono<T> mono(@NonNull Recorder<?> recorder,
			@NonNull IntervalRecording<?> recording,
			@NonNull Supplier<Mono<T>> supplier) {
		return runMonoSupplierInScope(supplier, (o, span1) -> {
		}).contextWrite(context -> ReactorObservability.makeRecordingCurrent(tracer, context, span));
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param tracer - Tracer bean
	 * @param currentTraceContext - CurrentTraceContext bean
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @param spanCustomizer - customizer for the child span
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(@NonNull Tracer tracer, @NonNull CurrentTraceContext currentTraceContext,
			@NonNull String childSpanName, @NonNull Supplier<Flux<T>> supplier,
			@NonNull BiConsumer<T, Span> spanCustomizer) {
		return runFluxSupplierInScope(supplier, spanCustomizer).contextWrite(
				context -> ReactorObservability.enhanceContext(tracer, currentTraceContext, context, childSpanName));
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param tracer - Tracer bean
	 * @param currentTraceContext - CurrentTraceContext bean
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @param spanCustomizer - customizer for the child span
	 * @param spanFunction - function that creates a new or child span
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(@NonNull Tracer tracer, @NonNull CurrentTraceContext currentTraceContext,
			@NonNull String childSpanName, @NonNull Supplier<Flux<T>> supplier,
			@NonNull BiConsumer<T, Span> spanCustomizer, @NonNull Function<Span, Span> spanFunction) {
		return runFluxSupplierInScope(supplier, spanCustomizer).contextWrite(context -> ReactorObservability
				.enhanceContext(tracer, currentTraceContext, context, childSpanName, spanFunction));
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param tracer - Tracer bean
	 * @param span - span to put in context
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(@NonNull Tracer tracer, @NonNull Span span,
			@NonNull Supplier<Flux<T>> supplier) {
		return runFluxSupplierInScope(supplier, (o, span1) -> {
		}).contextWrite(context -> ReactorObservability.makeRecordingCurrent(tracer, context, span));
	}

	private static <T> Flux<T> runFluxSupplierInScope(Supplier<Flux<T>> supplier, BiConsumer<T, Span> spanCustomizer) {
		return Flux.deferContextual(contextView -> {
			Span span = contextView.get(Span.class);
			Tracer.SpanInScope scope = contextView.get(Tracer.SpanInScope.class);
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
						span.end();
						scope.close();
					});
			// @formatter:on
		});
	}

	/**
	 * Wraps the given Flux in a trace representation. Retrieves the span from context,
	 * creates a child span with the given name.
	 * @param tracer - Tracer bean
	 * @param currentTraceContext - CurrentTraceContext bean
	 * @param childSpanName - name of the created child span
	 * @param supplier - supplier of a {@link Flux} to be wrapped in tracing
	 * @param <T> - type returned by the Flux
	 * @return traced Flux
	 */
	public static <T> Flux<T> tracedFlux(@NonNull Tracer tracer, @NonNull CurrentTraceContext currentTraceContext,
			@NonNull String childSpanName, @NonNull Supplier<Flux<T>> supplier) {
		return tracedFlux(tracer, currentTraceContext, childSpanName, supplier, (o, span) -> {
		});
	}

	private static Span childSpanFromContext(Tracer tracer, CurrentTraceContext currentTraceContext,
			Context context, String childSpanName) {
		return childSpanFromContext(currentTraceContext, context, childSpanName,
				span -> span == null ? tracer.nextSpan() : tracer.nextSpan(span));
	}

	private static Span childSpanFromContext(CurrentTraceContext currentTraceContext,
			Context context, String childSpanName, Function<Span, Span> spanSupplier) {
		TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
		Span span = context.getOrDefault(Span.class, null);
		if (traceContext == null && span == null) {
			span = spanSupplier.apply(null);
			if (log.isDebugEnabled()) {
				log.debug("There was no previous span in reactor context, created a new one [" + span + "]");
			}
		}
		else if (traceContext != null && span == null) {
			// there was a previous span - we create a child one
			try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(traceContext)) {
				if (log.isDebugEnabled()) {
					log.debug("Found a trace context in reactor context [" + traceContext + "]");
				}
				span = spanSupplier.apply(null);
				if (log.isDebugEnabled()) {
					log.debug("Created a child span [" + span + "]");
				}
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
		return span.name(childSpanName).start();
	}

	/**
	 * Updates the Reactor context with tracing information. Creates a new span if there
	 * is no current span. Creates a child span if there was an entry in the context
	 * already.
	 * @param tracer tracer
	 * @param currentTraceContext current trace context
	 * @param context Reactor context
	 * @param childSpanName child span name when there is no span in context
	 * @param spanSupplier function that creates a new or child span
	 * @return updated Reactor context
	 */
	public static Context enhanceContext(Tracer tracer, CurrentTraceContext currentTraceContext,
			Context context, String childSpanName, Function<Span, Span> spanSupplier) {
		Span span = childSpanFromContext(currentTraceContext, context, childSpanName, spanSupplier);
		return makeRecordingCurrent(tracer, context, span);
	}

	/**
	 * Updates the Reactor context with tracing information. Creates a new span if there
	 * is no current span. Creates a child span if there was an entry in the context
	 * already.
	 * @param tracer tracer
	 * @param currentTraceContext current trace context
	 * @param context Reactor context
	 * @param childSpanName child span name when there is no span in context
	 * @return updated Reactor context
	 */
	public static Context enhanceContext(Recorder<?> recorder, Context context,
			String childSpanName) {
		Span span = childSpanFromContext(recorder, context, childSpanName);
		return makeRecordingCurrent(tracer, context, span);
	}

	/**
	 * Puts the provided span in scope and in Reactor context.
	 * @param tracer tracer
	 * @param context Reactor context
	 * @param span span to put in Reactor context
	 * @return mutated context
	 */
	public static Context makeRecordingCurrent(IntervalRecording<?> recording,
			Context context, Span span) {
		Context newContext = context.put(IntervalRecording.class, recording.restore());
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
	 * @param tracer tracer
	 * @param currentTraceContext current trace context
	 * @param context context view
	 * @return span from Reactor context or creates a new one if missing
	 */
	public static Span spanFromContext(Tracer tracer, CurrentTraceContext currentTraceContext, ContextView context) {
		Span span = context.getOrDefault(Span.class, null);
		if (span != null) {
			if (log.isDebugEnabled()) {
				log.debug("Found a span in reactor context [" + span + "]");
			}
			return span;
		}
		TraceContext traceContext = context.getOrDefault(TraceContext.class, null);
		if (traceContext != null) {
			try (CurrentTraceContext.Scope scope = currentTraceContext.maybeScope(traceContext)) {
				if (log.isDebugEnabled()) {
					log.debug("Found a trace context in reactor context [" + traceContext + "]");
				}
				return tracer.currentSpan();
			}
		}
		Span newSpan = tracer.nextSpan().start();
		if (log.isDebugEnabled()) {
			log.debug("No span was found - will create a new one [" + newSpan + "]");
		}
		return newSpan;
	}

}

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
