/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.core;

import com.hazelcast.config.QuorumConfig;
import com.hazelcast.cp.CPSubsystem;

import java.util.concurrent.TimeUnit;

/**
 * ICountDownLatch is a backed-up distributed alternative to the
 * {@link java.util.concurrent.CountDownLatch java.util.concurrent.CountDownLatch}.
 * <p>
 * ICountDownLatch is a cluster-wide synchronization aid
 * that allows one or more threads to wait until a set of operations being
 * performed in other threads completes.
 * <p>
 * There are a few differences compared to the
 * {@link java.util.concurrent.CountDownLatch java.util.concurrent.CountDownLatch}:
 * <ol>
 * <li>
 * the ICountDownLatch count can be reset using {@link #trySetCount(int)} after
 * a countdown has finished but not during an active count. This allows
 * the same latch instance to be reused.
 * </li>
 * <li>
 * There is no await() method to do an unbound wait since this is undesirable
 * in a distributed application: for example, a cluster can split or the master
 * and replicas could all die. In most cases, it is best to configure
 * an explicit timeout so you have the ability to deal with these situations.
 * </li>
 * </ol>
 * As of version 3.12, Hazelcast offers 2 different {@link ICountDownLatch}
 * impls. Behaviour of {@link ICountDownLatch} under failure scenarios,
 * including network partitions, depend on the impl. The first impl is the
 * one accessed via {@link HazelcastInstance#getCountDownLatch(String)}.
 * This impl works on top of Hazelcast's async replication algorithm and
 * does not guarantee linearizability during failures. During a split, each
 * partitioned cluster will either create a brand new and uninitialised (zero'd)
 * {@link ICountDownLatch} instance, or it will continue to use the primary or
 * backup replica. For example, it is possible for both the primary and backup
 * replicas to be resident in one cluster partition, and for another one to be
 * created as new in the other side of the network partition. In any of these
 * cases, the counter value in the respective {@link ICountDownLatch} instance
 * may diverge.
 * <p>
 * In this impl, when the split heals, Hazelcast performs a default largest
 * cluster wins resolution, or a random winner is chosen where clusters sizes
 * are equal. This can lead to situations where the {@link ICountDownLatch}
 * can fall into an unpredictable state, and a countdown to zero may never be
 * achieved.
 * <p>
 * If required, when using {@link ICountDownLatch} as an orchestration
 * mechanism, you should assess the state of the orchestration outcome and
 * the associated countdown actors after a split-brain heal has taken place,
 * and take steps to re-orchestrate if needed.
 * <p>
 * This {@link ICountDownLatch} impl also supports Quorum {@link QuorumConfig}
 * in cluster versions 3.10 and higher. However, Hazelcast quorums do not
 * guarantee strong consistency under failure scenarios.
 * <p>
 * The second {@link ICountDownLatch} impl is a new one introduced with the
 * {@link CPSubsystem} in version 3.12. It is accessed via
 * {@link CPSubsystem#getCountDownLatch(String)}. It has a major difference to
 * the old implementation, that is, it works on top of the Raft consensus
 * algorithm. It offers linearizability during crash failures and network
 * partitions. It is CP with respect to the CAP principle. If a network
 * partition occurs, it remains available on at most one side of the partition.
 * <p>
 * All of the API methods in the new CP {@link ICountDownLatch} impl offer
 * the exactly-once execution semantics. For instance, even if
 * a {@link #countDown()} call is internally retried because of crashed
 * Hazelcast member, the counter value is decremented only once.
 */
public interface ICountDownLatch extends DistributedObject {

    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, or an exception is thrown, or the specified waiting time elapses.
     * <p>
     * If the current count is zero then this method returns immediately
     * with the value {@code true}.
     * <p>
     * If the current count is greater than zero, then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of five things happen:
     * <ul>
     * <li>the count reaches zero due to invocations of the
     * {@link #countDown} method,
     * <li>this ICountDownLatch instance is destroyed,
     * <li>the countdown owner becomes disconnected,
     * <li>some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread, or
     * <li>the specified waiting time elapses.
     * </ul>
     * If the count reaches zero, then the method returns with the
     * value {@code true}.
     * <p>
     * If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method, or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     * <p>
     * If the specified waiting time elapses then the value {@code false}
     * is returned.  If the time is less than or equal to zero, the method
     * will not wait at all.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit of the {@code timeout} argument
     * @return {@code true} if the count reached zero, {@code false}
     * if the waiting time elapsed before the count reached zero
     * @throws InterruptedException  if the current thread is interrupted
     * @throws IllegalStateException if the Hazelcast instance is shutdown while waiting
     * @throws NullPointerException  if unit is null
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     * <p>
     * If the current count is greater than zero, then it is decremented.
     * If the new count is zero:
     * <ul>
     * <li>All waiting threads are re-enabled for thread scheduling purposes, and
     * <li>Countdown owner is set to {@code null}.
     * </ul>
     * If the current count equals zero, then nothing happens.
     */
    void countDown();

    /**
     * Returns the current count.
     *
     * @return the current count
     */
    int getCount();

    /**
     * Sets the count to the given value if the current count is zero.
     * <p>
     * If count is not zero, then this method does nothing and returns {@code false}.
     *
     * @param count the number of times {@link #countDown} must be invoked
     *              before threads can pass through {@link #await}
     * @return {@code true} if the new count was set, {@code false} if the current count is not zero
     * @throws IllegalArgumentException if {@code count} is negative
     */
    boolean trySetCount(int count);
}
