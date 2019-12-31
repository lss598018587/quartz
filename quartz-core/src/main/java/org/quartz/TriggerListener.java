
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

import org.quartz.Trigger.CompletedExecutionInstruction;

/**
 * The interface to be implemented by classes that want to be informed when a
 * <code>{@link Trigger}</code> fires. In general, applications that use a
 * <code>Scheduler</code> will not have use for this mechanism.
 * 
 * @see ListenerManager#addTriggerListener(TriggerListener, Matcher)
 * @see Matcher
 * @see Trigger
 * @see JobListener
 * @see JobExecutionContext
 * 
 * @author James House
 */
public interface TriggerListener {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * 用于获取触发器的名称
     */
    String getName();

    /**
     * 当与监听器相关联的Trigger被触发，Job上的execute()方法将被执行时，Scheduler就调用该方法。
     */
    void triggerFired(Trigger trigger, JobExecutionContext context);

    /**
     * 在 Trigger 触发后，Job 将要被执行时由 Scheduler 调用这个方法。
     * TriggerListener 给了一个选择去否决 Job 的执行。假如这个方法返回 true，这个 Job 将不会为此次 Trigger 触发而得到执行。
     */
    boolean vetoJobExecution(Trigger trigger, JobExecutionContext context);

    
    /**
     * Scheduler 调用这个方法是在 Trigger 错过触发时。
     * 你应该关注此方法中持续时间长的逻辑：在出现许多错过触发的 Trigger 时，长逻辑会导致骨牌效应。你应当保持这上方法尽量的小。
     */
    void triggerMisfired(Trigger trigger);

    /**
     * Trigger 被触发并且完成了 Job 的执行时，Scheduler 调用这个方法。
     */
    void triggerComplete(Trigger trigger, JobExecutionContext context,
            CompletedExecutionInstruction triggerInstructionCode);

}
