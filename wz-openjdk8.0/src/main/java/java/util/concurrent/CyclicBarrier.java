/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronization aid that allows a set of threads to all wait for
 * each other to reach a common barrier point.  CyclicBarriers are
 * useful in programs involving a fixed sized party of threads that
 * must occasionally wait for each other. The barrier is called
 * <em>cyclic</em> because it can be re-used after the waiting threads
 * are released.
 *
 * <p>A {@code CyclicBarrier} supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released.
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 *
 * <p><b>Sample usage:</b> Here is an example of using a barrier in a
 * parallel decomposition design:
 *
 * <pre> {@code
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *
 *   class Worker implements Runnable {
 *     int myRow;
 *     Worker(int row) { myRow = row; }
 *     public void run() {
 *       while (!done()) {
 *         processRow(myRow);
 *
 *         try {
 *           barrier.await();
 *         } catch (InterruptedException ex) {
 *           return;
 *         } catch (BrokenBarrierException ex) {
 *           return;
 *         }
 *       }
 *     }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     Runnable barrierAction =
 *       new Runnable() { public void run() { mergeRows(...); }};
 *     barrier = new CyclicBarrier(N, barrierAction);
 *
 *     List<Thread> threads = new ArrayList<Thread>(N);
 *     for (int i = 0; i < N; i++) {
 *       Thread thread = new Thread(new Worker(i));
 *       threads.add(thread);
 *       thread.start();
 *     }
 *
 *     // wait until done
 *     for (Thread thread : threads)
 *       thread.join();
 *   }
 * }}</pre>
 * <p>
 * Here, each worker thread processes a row of the matrix then waits at the
 * barrier until all rows have been processed. When all rows are processed
 * the supplied {@link Runnable} barrier action is executed and merges the
 * rows. If the merger
 * determines that a solution has been found then {@code done()} will return
 * {@code true} and each worker will terminate.
 *
 * <p>If the barrier action does not rely on the parties being suspended when
 * it is executed, then any of the threads in the party could execute that
 * action when it is released. To facilitate this, each invocation of
 * {@link #await} returns the arrival index of that thread at the barrier.
 * You can then choose which thread should execute the barrier action, for
 * example:
 * <pre> {@code
 * if (barrier.await() == 0) {
 *   // log the completion of this iteration
 * }}</pre>
 *
 * <p>The {@code CyclicBarrier} uses an all-or-none breakage model
 * for failed synchronization attempts: If a thread leaves a barrier
 * point prematurely because of interruption, failure, or timeout, all
 * other threads waiting at that barrier point will also leave
 * abnormally via {@link BrokenBarrierException} (or
 * {@link InterruptedException} if they too were interrupted at about
 * the same time).
 *
 * <p>Memory consistency effects: Actions in a thread prior to calling
 * {@code await()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions that are part of the barrier action, which in turn
 * <i>happen-before</i> actions following a successful return from the
 * corresponding {@code await()} in other threads.
 *
 * @author Doug Lea
 * @see CountDownLatch
 * @since 1.5
 */
public class CyclicBarrier {
    /**
     * Each use of the barrier is represented as a generation instance.
     * The generation changes whenever the barrier is tripped, or
     * is reset. There can be many generations associated with threads
     * using the barrier - due to the non-deterministic way the lock
     * may be allocated to waiting threads - but only one of these
     * can be active at a time (the one to which {@code count} applies)
     * and all the rest are either broken or tripped.
     * There need not be an active generation if there has been a break
     * but no subsequent reset.
     */
    /**
     * 静态内部类，代
     */
    private static class Generation {
        boolean broken = false;
    }

    /**
     * The lock for guarding barrier entry
     */
    // 重入锁，只有一个线程会执行成功，高并发情况下CyclicBarrier执行效率不是很高
    // CyclicBarrier的线程同步是依赖于 ReentrantLock 和 Condition 的。到达barrier的线程开始都会进入condition queue后阻塞，等待signal的到来；这些线程被唤醒后，将进入sync queue中排队抢锁，抢到锁的线程才能退出CyclicBarrier#await。
    //原文链接：https://blog.csdn.net/anlian523/article/details/106881158
    private final ReentrantLock lock = new ReentrantLock();
    /**
     * Condition to wait on until tripped
     */
    // 实现线程之间的通信，阻塞和唤醒线程用的
    private final Condition trip = lock.newCondition();


    /**
     * The number of parties
     */
    // 每次通过barrier一共需要的线程数量，final的
    // 每次通过barrier后需要靠它重新复位计数器
    private final int parties;
    /* The command to run when tripped */


    // 每一代线程通过barrier之前要做的事情，我们执行它的run()方法时根本不需要新起一个线程来执行
    private final Runnable barrierCommand;
    /**
     * The current generation
     */
    // 直接比较g1 == g2对象的地址来判断是否为同一个版本
    private Generation generation = new Generation();

    /**
     * Number of parties still waiting. Counts down from parties to 0
     * on each generation.  It is reset to parties on each new
     * generation or when broken.
     */
    // Number of parties still waiting,当前还需要多少个线程到达barrier,初始值是parties,通过barrier时它肯定变成0了
    private int count;

    /**
     * Updates state on barrier trip and wakes up everyone.
     * Called only while holding lock.
     */
    /**
     * nextGeneration()用来开启新的一代
     * 每一组一起通过barrier的线程我们称它们为一代
     * 正常情况下，一代中的最后一个调用CyclicBarrier#await的线程将执行此方法
     * 此方法执行期间一直是持有锁的
     */
    private void nextGeneration() {
        // signal completion of last generation
        // 当计数器减少为零时证明这代已经满了，唤醒条件队列上的所有当前代阻塞的线程
        // 执行nextGeneration之前就已经获得了lock锁
        trip.signalAll();
        // set up next generation
        // 换代时将parties定值赋值给count,count负责减少1
        // 恢复count的值，因为需要重新计数
        count = parties;
        // 上一代已经结束了，重新开启下一代
        generation = new Generation();
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.
     * Called only while holding lock.
     */
    /**
     * 打破计数器限制，
     * 不管当前到达barrier的线程够不够，直接让当前已到达barrier的线程们通过barrier
     * 全程持有锁
     */
    private void breakBarrier() {
        generation.broken = true;
        count = parties;
        trip.signalAll();
    }

    /**
     * Main barrier code, covering the various policies.
     * 退出await()的形式：
     */
    private int dowait(boolean timed, long nanos)
            throws InterruptedException, BrokenBarrierException,
            TimeoutException {
        // 多线程并发情况下只有一个线程能获得锁
        final ReentrantLock lock = this.lock;
        // 整个执行过程必须先获得锁
        lock.lock();
        try {
            // 这个Generation局部变量保存起来很重要，因为CyclicBarrier的generation成员在通过barrier后会替换为新对象
            // 获得的Generation对象肯定是当前线程应该在的“代”

            /**
             * 获得的Generation对象一定是当前线程所在的代吗？
             * 答：一定；修改代的方法为reset()和await()最后一个线程到达栅栏，并且执行预定义操作没报错，才会开启下一代
             * 这两个方法都要先获得互斥锁，获得的Generation对象肯定是当前线程应该在的“代”
             */
            final Generation g = generation;
            // 判断当前代是否有线程被break过
            if (g.broken)
                // 可检查异常
                // 因为当前代某个线程被中断，导致其他所有到达并阻塞屏障点的线程被唤醒，再次执行await()方式会抛出BrokenBarrierException
                throw new BrokenBarrierException();

            // 判断当前线程是否被中断，并清除中断标识
            if (Thread.interrupted()) {
                breakBarrier();
                // 抛中断异常
                throw new InterruptedException();
            }

            // 到达barrier时，将count减一的值，这个值作为正常返回时的返回值
            int index = --count;
            // 通过barrrier时，count变为0
            if (index == 0) {  // tripped
                // 当前代到达的最后一个线程，即之前已经到达了parties-1个线程
                // 用来标记command#run是否出错
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        //当代中最后一个线程，负责执行command.run()
                        command.run();
                    // 标记command#run执行过程中没有出错
                    ranAction = true;
                    // 唤醒条件队列的所有线程，开启下一代，count重新计数
                    nextGeneration();
                    return 0;
                } finally {
                    // 执行过程出错，则执行breakBarrier
                    if (!ranAction)
                        breakBarrier();
                }
            }

            /**
             * 执行到这里，说明当前线程肯定不是当代中的最后一个线程（即index不为0）
             * 如果最后一个线程执行command.run()没出错，将return 0返回
             * 如果最后一个线程执行command.run()出错了，执行finally块后，将返回上层函数并抛出异常
             */

            // 循环不会结束，直到tripped, broken, interrupted, or timed out
            // loop until tripped, broken, interrupted, or timed out
            for (; ; ) {
                try {
                    // 1. 普通版本调用的await,那么调用Condition#await
                    if (!timed)
                        trip.await();
                    else if (nanos > 0L)
                        // 如果是超时版本的，调用Condition#awaitNanos()
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    // 执行到这里，说明上面两种Condition的await，被中断而唤醒
                    // 如果局部变量和成员变量是同一个对象，且当前不是broken的，则先breakBarrier
                    if (g == generation && !g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        /**
                         * 执行到这里有两种情况：
                         * 1. g != generation, 已经换代，此时再去breakBarrier只能影响到新一代成员，所以不能再执行breakBarrier
                         * 2. g == generation, 但已经broken了。既然barrier已经被打破(breakBarrier()已经执行过了)，那就不用再执行barrier了
                         */
                        Thread.currentThread().interrupt();
                    }
                }
                //只要自己这一代是broken的，就抛出异常。只有breakBarrier函数会改变broken，这里给出执行breakBarrier的场景：
                //1. 当代的其他线程调用CyclicBarrier#await之前就已经被中断了（强调“其他”是因为，如果是这个原因，不会执行到这里）
                //2. 当代的其他线程调用Condition#await阻塞后被中断（上面的catch块）
                //3. 最后到达barrier的线程执行barrierCommand时出错了
                //4. reset方法被执行，这可以是任意线程
                if (g.broken)
                    throw new BrokenBarrierException();

                // 执行到这里，说明当代g不是broken的，且最后一个线程已经到达barrier
                //（最后一个到达了，会执行nextGeneration更新generation成员的）
                if (g != generation)
                    return index;

                // 执行到这里，说明当代g不是broken的，CyclicBarrier的代也没有更新呢
                // 说明最后一个线程还没来，自己就超时了
                // 如果发现已经超时，就打破barrier，抛出异常
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    /**
     * CyclicBarrier支持可选的Runnable命令，在一组线程中的最后一个线程到达屏障点之后(在所有线程被唤醒之前)，该命令只在所有线程到达屏障点之后运行一次
     * 该命令由最后一个进入屏障点的线程执行
     * <p>
     * parties代表每代通过barrier的线程数
     *
     * @param parties
     * @param barrierAction
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        //
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new {@code CyclicBarrier} that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *                before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @return the arrival index of the current thread, where index
     *         {@code getParties() - 1} indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was
     *         broken when {@code await} was called, or the barrier
     *         action (if present) failed due to an exception
     */
    /**
     * 是响应中断的，比如拦截10个线程，当其中一个线程中断了，那么拦截的所有线程都会抛出中断异常
     * <p>
     * 1. 计数器减一
     * 2. 阻塞等待，直到线程到齐tripped,BrokenBarrier,中断interrupted，超时timed out
     *
     * @return
     * @throws InterruptedException
     * @throws BrokenBarrierException
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * {@code await} on this barrier, or the specified waiting time elapses.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>The specified timeout elapses; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown. If the time is less than or equal to zero, the
     * method will not wait at all.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * {@code await} is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while
     * waiting, then all other waiting threads will throw {@link
     * BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @param timeout the time to wait for the barrier
     * @param unit    the time unit of the timeout parameter
     * @return the arrival index of the current thread, where index
     * {@code getParties() - 1} indicates the first
     * to arrive and zero indicates the last to arrive
     * @throws InterruptedException   if the current thread was interrupted
     *                                while waiting
     * @throws TimeoutException       if the specified timeout elapses.
     *                                In this case the barrier will be broken.
     * @throws BrokenBarrierException if <em>another</em> thread was
     *                                interrupted or timed out while the current thread was
     *                                waiting, or the barrier was reset, or the barrier was broken
     *                                when {@code await} was called, or the barrier action (if
     *                                present) failed due to an exception
     */
    public int await(long timeout, TimeUnit unit)
            throws InterruptedException,
            BrokenBarrierException,
            TimeoutException {
        return dowait(true, unit.toNanos(timeout));
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     * barrier due to interruption or timeout since
     * construction or the last reset, or a barrier action
     * failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    /**
     * 重置栅栏到初始状态
     * 如果有线程在await()过程中发现自己这一代是broken的，则会抛出BrokenBarrierException异常
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 打破当前代
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
