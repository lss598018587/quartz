
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
 * The interface to be implemented by classes that want to be informed when a
 * <code>{@link org.quartz.JobDetail}</code> executes. In general,
 * applications that use a <code>Scheduler</code> will not have use for this
 * mechanism.
 * 
 * @see ListenerManager#addJobListener(JobListener, Matcher)
 * @see Matcher
 * @see Job
 * @see JobExecutionContext
 * @see JobExecutionException
 * @see TriggerListener
 * 
 * @author James House
 */
public interface JobListener {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    /**
     * 用于获取该JobListener的名称。
     */
    String getName();

    /**
     * Scheduler在JobDetail将要被执行时调用这个方法。
     */
    void jobToBeExecuted(JobExecutionContext context);

    /**
     * Scheduler在JobDetail即将被执行，但又被TriggerListerner否决时会调用该方法
     * 
     * @see #jobToBeExecuted(JobExecutionContext)
     */
    void jobExecutionVetoed(JobExecutionContext context);

    
    /**
     * Scheduler在JobDetail被执行之后调用这个方法
     */
    void jobWasExecuted(JobExecutionContext context,
            JobExecutionException jobException);

}
