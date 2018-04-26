/*
 * Copyright (c) 2011-2018 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.time.Duration;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import reactor.core.CoreSubscriber;
import reactor.core.Disposable;
import reactor.util.annotation.Nullable;

/**
 * @author Simon Baslé
 */
public interface Broadcaster<T> extends Processor<T, T>, Disposable, CoreSubscriber<T> {

	/**
	 * Return the produced {@link Throwable} error if any or null
	 *
	 * @return the produced {@link Throwable} error if any or null
	 */
	@Nullable
	Throwable getError();

	/**
	 * Return true if terminated with onComplete
	 *
	 * @return true if terminated with onComplete
	 */
	default boolean hasCompleted() {
		return isTerminated() && getError() == null;
	}

	/**
	 * Return true if terminated with onError
	 *
	 * @return true if terminated with onError
	 */
	default boolean hasError() {
		return isTerminated() && getError() != null;
	}

	/**
	 * Indicates whether this {@link Broadcaster} has been terminated by the
	 * source producer with a success or an error.
	 *
	 * @return {@code true} if this {@link Broadcaster} is successful, {@code false} otherwise.
	 */
	boolean isTerminated();

	/**
	 * Forcibly terminate the {@link Broadcaster}, preventing it to be reused and
	 * resubscribed.
	 */
	@Override
	void dispose();

	/**
	 * Indicates whether this {@link Broadcaster} has been terminated by calling its
	 * {@link #dispose()} method.
	 *
	 * @return true if the {@link Broadcaster} has been terminated.
	 */
	@Override
	boolean isDisposed();

	/**
	 * For asynchronous {@link Broadcaster}, which maintain heavy resources
	 * (such as {@link Processors#fanOut()}), this method attempts to forcibly shutdown
	 * these resources, unlike {@link #dispose()} which would let the {@link Broadcaster}
	 * tear down the resources gracefully.
	 * <p>
	 * Since for asynchronous {@link Broadcaster} there could be undistributed values at
	 * this point, said values are returned as a {@link Flux}.
	 * <p>
	 * For other implementations, this is equivalent to calling {@link #dispose()} and
	 * returns an {@link Flux#empty() empty Flux}.
	 *
	 * @return a {@link Flux} of the undistributed values for async {@link Broadcaster Broadcasters}
	 */
	default Flux<T> forceDispose() {
		dispose();
		return Flux.empty();
	}

	/**
	 * For {@link Broadcaster} that maintain heavy resources (such as {@link Processors#fanOut()}),
	 * this method attempts to shutdown these resources gracefully within the given {@link Duration}.
	 * Unlike {@link #dispose()}, this <strong>blocks</strong> for the given {@link Duration}.
	 *
	 * <p>
	 * For other implementations, this is equivalent to calling {@link #dispose()}, returning
	 * the result of {@link #isDisposed()} immediately.
	 *
	 * @param timeout the timeout value as a {@link java.time.Duration}. Note this is
	 * converted to a {@link Long} * of nanoseconds (which amounts to roughly 292 years
	 * maximum timeout).
	 * @return if the underlying executor terminated and false if the timeout elapsed before
	 * termination
	 */
	default boolean disposeAndAwait(Duration timeout) {
		dispose();
		return isDisposed();
	}

	/**
	 * @return a snapshot number of available onNext before starving the resource
	 */
	long getAvailableCapacity();

	/**
	 * Return the number of active {@link Subscriber} or {@literal -1} if untracked.
	 *
	 * @return the number of active {@link Subscriber} or {@literal -1} if untracked
	 */
	long downstreamCount();

	/**
	 * Return true if any {@link Subscriber} is actively subscribed
	 *
	 * @return true if any {@link Subscriber} is actively subscribed
	 */
	default boolean hasDownstreams() {
		return downstreamCount() != 0L;
	}

	/**
	 * Return true if this {@link Broadcaster} supports multithread producing
	 *
	 * @return true if this {@link Broadcaster} supports multithread producing
	 */
	boolean isSerialized();

	/**
	 * Indicates whether this {@link Broadcaster} has been completed with an error.
	 *
	 * @return {@code true} if this {@link Broadcaster} was completed with an error, {@code false} otherwise.
	 */
	default boolean isError() {
		return hasError();
	}

	/**
	 * Indicates whether this {@link Broadcaster} has completed without error. For Mono-like
	 * {@link Broadcaster Broadcasters}, it also takes into account if the mono-processor
	 * was value, differentiating from {@link #hasCompleted()}.
	 *
	 * @return {@code true} if this {@link Broadcaster} is successful, {@code false} otherwise.
	 */
	default boolean isSuccess() {
		return hasCompleted();
	}

	/**
	 * Create a {@link FluxSink} that safely gates multi-threaded producer
	 * {@link Subscriber#onNext(Object)}. This processor will be subscribed to
	 * said {@link FluxSink}, and any previous subscribers will be unsubscribed.
	 *
	 * <p> The returned {@link FluxSink} will not apply any
	 * {@link FluxSink.OverflowStrategy} and overflowing {@link FluxSink#next(Object)}
	 * will behave in two possible ways depending on the Processor:
	 * <ul>
	 * <li> an unbounded processor will handle the overflow itself by dropping or
	 * buffering </li>
	 * <li> a bounded processor will block/spin</li>
	 * <li> a {@link Mono}-like processor will reject the {@link FluxSink#next(Object)} calls</li>
	 * </ul>
	 *
	 * @return a serializing {@link FluxSink}
	 */
	FluxSink<T> sink();

	/**
	 * Create a {@link FluxSink} that safely gates multi-threaded producer
	 * {@link Subscriber#onNext(Object)}. This processor will be subscribed to
	 * said {@link FluxSink}, and any previous subscribers will be unsubscribed.
	 *
	 * <p> The returned {@link FluxSink} will deal with overflowing {@link FluxSink#next(Object)}
	 * according to the selected {@link reactor.core.publisher.FluxSink.OverflowStrategy},
	 * unless the sink has {@link Mono}-like semantics (in which case it will reject
	 * multiple onNext calls anyway).
	 *
	 * @param strategy the overflow strategy, see {@link FluxSink.OverflowStrategy}
	 * for the available strategies
	 * @return a serializing {@link FluxSink}
	 */
	FluxSink<T> sink(FluxSink.OverflowStrategy strategy);

	/**
	 * Expose a Reactor {@link Flux} API on top of the {@link Broadcaster}'s output,
	 * allowing composition of operators on it.
	 *
	 * @implNote most implementations will already implement {@link Flux}
	 * and thus can return themselves.
	 *
	 * @return a full reactive {@link Flux} API on top of the {@link Broadcaster}'s output
	 */
	Flux<T> asFlux();

	/**
	 * Expose a Reactor {@link Mono} API on top of the {@link Broadcaster}'s output,
	 * allowing composition of operators on it. If the processor doesn't directly have
	 * {@link Mono}-like semantics, this is equivalent to calling {@link #asFlux()} then
	 * {@link Flux#next()}.
	 *
	 * @implNote Mono processor implementations will already implement {@link Mono}
	 * and thus can return themselves.
	 *
	 * @return a full reactive {@link Mono} API on top of the {@link Broadcaster}'s output
	 */
	default Mono<T> asMono() {
		return asFlux().next();
	}
}
