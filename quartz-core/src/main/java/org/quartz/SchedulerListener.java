
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

package org.quartz;

/**
 * The interface to be implemented by classes that want to be informed of major
 * <code>{@link Scheduler}</code> events.
 * 
 * @see Scheduler
 * @see JobListener
 * @see TriggerListener
 * 
 * @author James House
 */
public interface SchedulerListener {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * 用于部署JobDetail时调用
     */
    void jobScheduled(Trigger trigger);

    /**
     * 用于卸载JobDetail时调用
     */
    void jobUnscheduled(TriggerKey triggerKey);

    /**
     * 当一个 Trigger 来到了再也不会触发的状态时调用这个方法。除非这个 Job 已设置成了持久性，否则它就会从 Scheduler 中移除。
     */
    void triggerFinalized(Trigger trigger);

    /**
     * Scheduler 调用这个方法是发生在一个 Trigger 或 Trigger 组被暂停时。假如是 Trigger 组的话，triggerName 参数将为 null。
     */
    void triggerPaused(TriggerKey triggerKey);

    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> when a 
     * group of <code>{@link Trigger}s</code> has been paused.
     * </p>
     * 
     * <p>If all groups were paused then triggerGroup will be null</p>
     * 
     * @param triggerGroup the paused group, or null if all were paused
     */
    void triggersPaused(String triggerGroup);
    
    /**
     * Scheduler 调用这个方法是发生成一个 Trigger 或 Trigger 组从暂停中恢复时。假如是 Trigger 组的话，假如是 Trigger 组的话，triggerName 参数将为 null。参数将为 null。
     */
    void triggerResumed(TriggerKey triggerKey);

    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> when a 
     * group of <code>{@link Trigger}s</code> has been un-paused.
     * </p>
     */
    void triggersResumed(String triggerGroup);

    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> when a <code>{@link org.quartz.JobDetail}</code>
     * has been added.
     * </p>
     */
    void jobAdded(JobDetail jobDetail);
    
    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> when a <code>{@link org.quartz.JobDetail}</code>
     * has been deleted.
     * </p>
     */
    void jobDeleted(JobKey jobKey);
    
    /**
     * 当一个或一组 JobDetail 暂停时调用这个方法。
     */
    void jobPaused(JobKey jobKey);

    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> when a 
     * group of <code>{@link org.quartz.JobDetail}s</code> has been paused.
     * </p>
     * 
     * @param jobGroup the paused group, or null if all were paused
     */
    void jobsPaused(String jobGroup);
    
    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> when a <code>{@link org.quartz.JobDetail}</code>
     * has been un-paused.
     * </p>
     */
    void jobResumed(JobKey jobKey);

    /**
     * 当一个或一组 Job 从暂停上恢复时调用这个方法。假如是一个 Job 组，jobName 参数将为 null。
     */
    void jobsResumed(String jobGroup);

    /**
     * 在 Scheduler 的正常运行期间产生一个严重错误时调用这个方法。
     */
    void schedulerError(String msg, SchedulerException cause);

    /**
     *   当Scheduler处于StandBy模式时，调用该方法
     */
    void schedulerInStandbyMode();

    /**
     *  当Scheduler 开启时，调用该方法
     */
    void schedulerStarted();
    
    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> to inform the listener
     * that it is starting.
     * </p>
     */
    void schedulerStarting();
    
    /**
     * <p>
     * Called by the <code>{@link Scheduler}</code> to inform the listener
     * that it has shutdown.
     * </p>
     */
    void schedulerShutdown();
    
    /**
     * 当Scheduler停止时，调用该方法
     */
    void schedulerShuttingdown();

    /**
     * 当Scheduler中的数据被清除时，调用该方法。
     */
    void schedulingDataCleared();
}
