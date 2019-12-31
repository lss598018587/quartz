
/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.quartz.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.quartz.JobPersistenceException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.spi.JobStore;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;
import org.quartz.spi.TriggerFiredResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * The thread responsible for performing the work of firing <code>{@link Trigger}</code>
 * s that are registered with the <code>{@link QuartzScheduler}</code>.
 * </p>
 *
 * @see QuartzScheduler
 * @see org.quartz.Job
 * @see Trigger
 *
 * @author James House
 */
public class QuartzSchedulerThread extends Thread {
    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *
     * Data members.
     *
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */
    private QuartzScheduler qs;

    private QuartzSchedulerResources qsRsrcs;

    private final Object sigLock = new Object();

    private boolean signaled;
    private long signaledNextFireTime;

    private boolean paused;

    /**
     * 是否停下来
     */
    private AtomicBoolean halted;

    private Random random = new Random(System.currentTimeMillis());

    // When the scheduler finds there is no current trigger to fire, how long
    // it should wait until checking again...
    private static long DEFAULT_IDLE_WAIT_TIME = 30L * 1000L;

    private long idleWaitTime = DEFAULT_IDLE_WAIT_TIME;

    private int idleWaitVariablness = 7 * 1000;

    private final Logger log = LoggerFactory.getLogger(getClass());

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *
     * Constructors.
     *
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * <p>
     * Construct a new <code>QuartzSchedulerThread</code> for the given
     * <code>QuartzScheduler</code> as a non-daemon <code>Thread</code>
     * with normal priority.
     * </p>
     */
    QuartzSchedulerThread(QuartzScheduler qs, QuartzSchedulerResources qsRsrcs) {
        this(qs, qsRsrcs, qsRsrcs.getMakeSchedulerThreadDaemon(), Thread.NORM_PRIORITY);
    }

    /**
     * <p>
     * Construct a new <code>QuartzSchedulerThread</code> for the given
     * <code>QuartzScheduler</code> as a <code>Thread</code> with the given
     * attributes.
     * </p>
     */
    QuartzSchedulerThread(QuartzScheduler qs, QuartzSchedulerResources qsRsrcs, boolean setDaemon, int threadPrio) {
        super(qs.getSchedulerThreadGroup(), qsRsrcs.getThreadName());
        this.qs = qs;
        this.qsRsrcs = qsRsrcs;
        this.setDaemon(setDaemon);
        if(qsRsrcs.isThreadsInheritInitializersClassLoadContext()) {
            log.info("QuartzSchedulerThread Inheriting ContextClassLoader of thread: " + Thread.currentThread().getName());
            this.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        }

        this.setPriority(threadPrio);

        // start the underlying thread, but put this object into the 'paused'
        // state
        // so processing doesn't start yet...
        paused = true;
        halted = new AtomicBoolean(false);
    }

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     *
     * Interface.
     *
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    void setIdleWaitTime(long waitTime) {
        idleWaitTime = waitTime;
        idleWaitVariablness = (int) (waitTime * 0.2);
    }

    private long getRandomizedIdleWaitTime() {
        return idleWaitTime - random.nextInt(idleWaitVariablness);
    }

    /**
     * pause为true，发出让主循环暂停的信号，以便线程在下一个可处理的时刻暂停
     * pause为false，唤醒sigLock对象的所有等待队列的线程
     */
    void togglePause(boolean pause) {
        synchronized (sigLock) {
            paused = pause;

            if (paused) {
                signalSchedulingChange(0);
            } else {
                sigLock.notifyAll();
            }
        }
    }

    /**
     * <p>
     * Signals the main processing loop to pause at the next possible point.
     * </p>
     */
    void halt(boolean wait) {
        synchronized (sigLock) {
            halted.set(true);

            if (paused) {
                sigLock.notifyAll();
            } else {
                signalSchedulingChange(0);
            }
        }
        
        if (wait) {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        join();
                        break;
                    } catch (InterruptedException _) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    boolean isPaused() {
        return paused;
    }

    /**
     * <p>
     * Signals the main processing loop that a change in scheduling has been
     * made - in order to interrupt any sleeping that may be occuring while
     * waiting for the fire time to arrive.
     * </p>
     *
     * @param candidateNewNextFireTime the time (in millis) when the newly scheduled trigger
     * will fire.  If this method is being called do to some other even (rather
     * than scheduling a trigger), the caller should pass zero (0).
     */
    public void signalSchedulingChange(long candidateNewNextFireTime) {
        synchronized(sigLock) {
            signaled = true;
            signaledNextFireTime = candidateNewNextFireTime;
            sigLock.notifyAll();
        }
    }

    public void clearSignaledSchedulingChange() {
        synchronized(sigLock) {
            signaled = false;
            signaledNextFireTime = 0;
        }
    }

    public boolean isScheduleChanged() {
        synchronized(sigLock) {
            return signaled;
        }
    }

    public long getSignaledNextFireTime() {
        synchronized(sigLock) {
            return signaledNextFireTime;
        }
    }

    /**
     * <p>
     *  调度器线程一旦启动，将一直运行此方法
     * </p>
     */
    @Override
    public void run() {
        int acquiresFailed = 0;

        // 只有调用了halt()方法，才会退出这个死循环
        // while()无限循环，每次循环取出时间将到的trigger，触发对应的job，直到调度器线程被关闭
        while (!halted.get()) {
            try {
                /**
                 * Part A：如果是暂停状态，那么循环超时等待1000毫秒
                 */
                synchronized (sigLock) {
                    while (paused && !halted.get()) {
                        try {
                            // 这里会不断循环等待，直到QuartzScheduler.start()调用了togglePause(false)
                            // 调用wait()，调度器线程进入休眠状态，同时sigLock锁被释放
                            // togglePause(false)获得sigLock锁，将paused置为false，使调度器线程能够退出此循环，同时执行sigLock.notifyAll()唤醒调度器线程
                            sigLock.wait(1000L);
                        } catch (InterruptedException ignore) {
                        }

                        // 上一步获取成功将失败标志置为0;
                        acquiresFailed = 0;
                    }

                    if (halted.get()) {
                        break;
                    }
                }

                //失败了就休眠一下，等系统修复。比如存储是db的话，就等db重启
                if (acquiresFailed > 1) {
                    try {
                        long delay = computeDelayForRepeatedErrors(qsRsrcs.getJobStore(), acquiresFailed);
                        Thread.sleep(delay);
                    } catch (Exception ignore) {
                    }
                }

                // blockForAvailableThreads的语义：阻塞直到有空闲的线程可用，然后返回其数量
                int availThreadCount = qsRsrcs.getThreadPool().blockForAvailableThreads();
                if(availThreadCount > 0) {
                    /**
                     * Part B：获取acquire状态的Trigger列表
                     */
                    List<OperableTrigger> triggers;

                    long now = System.currentTimeMillis();

                    clearSignaledSchedulingChange();
                    try {
                        // 获取马上到时间的trigger
                        // 查找30秒内一定数量的Trigger
                        // 允许取出的trigger个数不能超过一个阀值，这个阀值是线程池个数与org.quartz.scheduler.batchTriggerAcquisitionMaxCount配置值间的最小者
                        //参数1:nolaterthan = now+3000ms,参数2 最大获取数量,大小取线程池线程剩余量与定义值得较小者
                        //参数3 时间窗口 默认为0,程序会在nolaterthan后加上窗口大小来选择trigger
                        triggers = qsRsrcs.getJobStore().acquireNextTriggers(
                                now + idleWaitTime, Math.min(availThreadCount, qsRsrcs.getMaxBatchSize()), qsRsrcs.getBatchTimeWindow());
                        //上一步获取成功将失败标志置为0;
                        acquiresFailed = 0;
                        if (log.isDebugEnabled())
                            log.debug("batch acquisition of " + (triggers == null ? 0 : triggers.size()) + " triggers");
                    } catch (JobPersistenceException jpe) {
                        if (acquiresFailed == 0) {
                            qs.notifySchedulerListenersError(
                                "An error occurred while scanning for the next triggers to fire.",
                                jpe);
                        }
                        //捕捉到异常则值标志增加,再次尝试获取
                        if (acquiresFailed < Integer.MAX_VALUE)
                            acquiresFailed++;
                        continue;
                    } catch (RuntimeException e) {
                        if (acquiresFailed == 0) {
                            getLog().error("quartzSchedulerThreadLoop: RuntimeException "
                                    +e.getMessage(), e);
                        }
                        //捕捉到异常则值标志增加,再次尝试获取
                        if (acquiresFailed < Integer.MAX_VALUE)
                            acquiresFailed++;
                        continue;
                    }


                    if (triggers != null && !triggers.isEmpty()) {

                        /**
                         * Part C：获取List第一个Trigger的下次触发时刻
                         * Part D：设置Triggers为'executing'状态
                         * Part E：执行Job
                         */
                        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        now = System.currentTimeMillis();
                        long triggerTime = triggers.get(0).getNextFireTime().getTime();
                        long timeUntilTrigger = triggerTime - now;
//                        System.out.println(Thread.currentThread().getId()+"停在了这里:"+format.format(new Date()));
                        while(timeUntilTrigger > 2) {
                            synchronized (sigLock) {
                                if (halted.get()) {
                                    break;
                                }
                                //如果这时调度器发生了改变,新的trigger添加进来,那么有可能新添加的trigger比当前待执行的trigger
                                //更急迫,那么需要放弃当前trigger重新获取,然而,这里存在一个值不值得的问题,如果重新获取新trigger
                                //的时间要长于当前时间到新trigger出发的时间,那么即使放弃当前的trigger,仍然会导致xntrigger获取失败,
                                //但我们又不知道获取新的trigger需要多长时间,于是,我们做了一个主观的评判,若jobstore为RAM,那么
                                //假定获取时间需要7ms,若jobstore是持久化的,假定其需要70ms,当前时间与新trigger的触发时间之差小于
                                // 这个值的我们认为不值得重新获取,返回false
                                //这里判断是否有上述情况发生,值不值得放弃本次trigger,若判定不放弃,则线程直接等待至trigger触发的时刻
                                if (!isCandidateNewTimeEarlierWithinReason(triggerTime, false)) {
                                    try {
                                        // we could have blocked a long while
                                        // on 'synchronize', so we must recompute
                                        now = System.currentTimeMillis();
                                        timeUntilTrigger = triggerTime - now;
                                        //todo 已经决定放弃了，为什么这里还要等待
                                        if(timeUntilTrigger >= 1)
                                            sigLock.wait(timeUntilTrigger);
                                    } catch (InterruptedException ignore) {
                                    }
                                }
                            }
//                            System.out.println(Thread.currentThread().getId()+"just开始在了这里"+format.format(new Date()));
                            //该方法调用了上面的判定方法,作为再次判定的逻辑
                            //到达这里有两种情况
                            // 1.决定放弃当前trigger,那么再判定一次,如果仍然有放弃,那么清空triggers列表并退出循环
                            // 2.不放弃当前trigger,且线程已经wait到trigger触发的时刻,那么什么也不做
                            if(releaseIfScheduleChangedSignificantly(triggers, triggerTime)) {
                                break;
                            }
                            now = System.currentTimeMillis();
                            timeUntilTrigger = triggerTime - now;
                            //这时触发器已经即将触发,值会<2
                        }

                        // this happens if releaseIfScheduleChangedSignificantly decided to release triggers
                        if(triggers.isEmpty())
                            continue;

                        // set triggers to 'executing'
                        List<TriggerFiredResult> bndles = new ArrayList<TriggerFiredResult>();

                        boolean goAhead = true;
                        synchronized(sigLock) {
                            goAhead = !halted.get();
                        }
                        if(goAhead) {
                            try {
                                //触发triggers,结果付给bndles,注意,从这里返回后,trigger在数据库中已经经过了锁定,解除锁定,这一套过程
                                //所以说,quratz定不是等到job执行完才释放trigger资源的占有,而是读取完本次触发所需的信息后立即释放资源
                                //然后再执行jobs
                                List<TriggerFiredResult> res = qsRsrcs.getJobStore().triggersFired(triggers);
                                if(res != null)
                                    bndles = res;
                            } catch (SchedulerException se) {
                                qs.notifySchedulerListenersError(
                                        "An error occurred while firing triggers '"
                                                + triggers + "'", se);
                                //QTZ-179 : a problem occurred interacting with the triggers from the db
                                //we release them and loop again
                                for (int i = 0; i < triggers.size(); i++) {
                                    qsRsrcs.getJobStore().releaseAcquiredTrigger(triggers.get(i));
                                }
                                continue;
                            }

                        }

//                        System.out.println(Thread.currentThread().getId()+"走过来，要执行了");

                        //迭代trigger的信息,分别跑job
                        for (int i = 0; i < bndles.size(); i++) {
                            TriggerFiredResult result =  bndles.get(i);
                            TriggerFiredBundle bndle =  result.getTriggerFiredBundle();
                            Exception exception = result.getException();

                            if (exception instanceof RuntimeException) {
                                getLog().error("RuntimeException while firing trigger " + triggers.get(i), exception);
                                qsRsrcs.getJobStore().releaseAcquiredTrigger(triggers.get(i));
                                continue;
                            }

                            // it's possible to get 'null' if the triggers was paused,
                            // blocked, or other similar occurrences that prevent it being
                            // fired at this time...  or if the scheduler was shutdown (halted)
                            //在特殊情况下,bndle可能为null,看triggerFired方法可以看到,当从数据库获取trigger时,如果status不是
                            //STATE_ACQUIRED,那么会直接返回空.quratz这种情况下本调度器启动重试流程,重新获取4次,若仍有问题,
                            //则抛出异常.
                            if (bndle == null) {
                                qsRsrcs.getJobStore().releaseAcquiredTrigger(triggers.get(i));
                                continue;
                            }

                            //执行job
                            JobRunShell shell = null;
                            try {
                                //创建一个job的Runshell
                                shell = qsRsrcs.getJobRunShellFactory().createJobRunShell(bndle);
                                shell.initialize(qs);
                            } catch (SchedulerException se) {
                                qsRsrcs.getJobStore().triggeredJobComplete(triggers.get(i), bndle.getJobDetail(), CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR);
                                continue;
                            }
                            // 执行与trigger绑定的job
                            // shell是JobRunShell对象，实现了Runnable接口
                            // SimpleThreadPool.runInThread(Runnable)从线程池空闲列表中取出一个工作线程
                            // 工作线程执行WorkerThread.run(Runnable)，详见下方WorkerThread的讲解
                            if (qsRsrcs.getThreadPool().runInThread(shell) == false) {
                                // this case should never happen, as it is indicative of the
                                // scheduler being shutdown or a bug in the thread pool or
                                // a thread pool being used concurrently - which the docs
                                // say not to do...
                                getLog().error("ThreadPool.runInThread() return false!");
                                qsRsrcs.getJobStore().triggeredJobComplete(triggers.get(i), bndle.getJobDetail(), CompletedExecutionInstruction.SET_ALL_JOB_TRIGGERS_ERROR);
                            }

                        }

                        continue; // while (!halted)
                    }
                } else { // if(availThreadCount > 0)
                    // should never happen, if threadPool.blockForAvailableThreads() follows contract
                    continue; // while (!halted)
                }
                //保证负载平衡的方法,每次执行一轮触发后,本scheduler会等待一个随机的时间,这样就使得其他节点上的scheduler可以得到资源.
                long now = System.currentTimeMillis();
                long waitTime = now + getRandomizedIdleWaitTime();
                long timeUntilContinue = waitTime - now;
//                long timeUntilContinue =getRandomizedIdleWaitTime();
                synchronized(sigLock) {
                    try {
                      if(!halted.get()) {
                        // QTZ-336 A job might have been completed in the mean time and we might have
                        // missed the scheduled changed signal by not waiting for the notify() yet
                        // Check that before waiting for too long in case this very job needs to be
                        // scheduled very soon
                        if (!isScheduleChanged()) {
                          sigLock.wait(timeUntilContinue);
                        }
                      }
                    } catch (InterruptedException ignore) {
                    }
                }

            } catch(RuntimeException re) {
                getLog().error("Runtime error occurred in main trigger firing loop.", re);
            }
        } // while (!halted)

        // drop references to scheduler stuff to aid garbage collection...
        qs = null;
        qsRsrcs = null;
    }

    private static final long MIN_DELAY = 20;
    private static final long MAX_DELAY = 600000;

    private static long computeDelayForRepeatedErrors(JobStore jobStore, int acquiresFailed) {
        long delay;
        try {
            delay = jobStore.getAcquireRetryDelay(acquiresFailed);
        } catch (Exception ignored) {
            // we're trying to be useful in case of error states, not cause
            // additional errors..
            delay = 100;
        }


        // sanity check per getAcquireRetryDelay specification
        if (delay < MIN_DELAY)
            delay = MIN_DELAY;
        if (delay > MAX_DELAY)
            delay = MAX_DELAY;

        return delay;
    }

    private boolean releaseIfScheduleChangedSignificantly(
            List<OperableTrigger> triggers, long triggerTime) {
        if (isCandidateNewTimeEarlierWithinReason(triggerTime, true)) {
            // above call does a clearSignaledSchedulingChange()
            for (OperableTrigger trigger : triggers) {
                qsRsrcs.getJobStore().releaseAcquiredTrigger(trigger);
            }
            triggers.clear();
            return true;
        }
        return false;
    }

    private boolean isCandidateNewTimeEarlierWithinReason(long oldTime, boolean clearSignal) {

        // So here's the deal: We know due to being signaled that 'the schedule'
        // has changed.  We may know (if getSignaledNextFireTime() != 0) the
        // new earliest fire time.  We may not (in which case we will assume
        // that the new time is earlier than the trigger we have acquired).
        // In either case, we only want to abandon our acquired trigger and
        // go looking for a new one if "it's worth it".  It's only worth it if
        // the time cost incurred to abandon the trigger and acquire a new one
        // is less than the time until the currently acquired trigger will fire,
        // otherwise we're just "thrashing" the job store (e.g. database).
        //
        // So the question becomes when is it "worth it"?  This will depend on
        // the job store implementation (and of course the particular database
        // or whatever behind it).  Ideally we would depend on the job store
        // implementation to tell us the amount of time in which it "thinks"
        // it can abandon the acquired trigger and acquire a new one.  However
        // we have no current facility for having it tell us that, so we make
        // a somewhat educated but arbitrary guess ;-).

        synchronized(sigLock) {

            if (!isScheduleChanged())
                return false;

            boolean earlier = false;

            if(getSignaledNextFireTime() == 0)
                earlier = true;
            else if(getSignaledNextFireTime() < oldTime )
                earlier = true;

            if(earlier) {
                // so the new time is considered earlier, but is it enough earlier?
                long diff = oldTime - System.currentTimeMillis();
                if(diff < (qsRsrcs.getJobStore().supportsPersistence() ? 70L : 7L))
                    earlier = false;
            }

            if(clearSignal) {
                clearSignaledSchedulingChange();
            }

            return earlier;
        }
    }

    public Logger getLog() {
        return log;
    }

} // end of QuartzSchedulerThread
