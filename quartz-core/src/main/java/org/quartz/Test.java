package org.quartz;

import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;
import static org.quartz.TriggerBuilder.newTrigger;

/**
 * @Auther: miaomiao
 * @Date: 2019-03-29 15:15
 * @Description:
 */
public class Test {
    private static Logger _logger = LoggerFactory.getLogger(Test.class);// log4j记录日志

    public static void main(String[] args) throws SchedulerException, InterruptedException {
        // 通过schedulerFactory获取一个调度器
        SchedulerFactory schedulerfactory = new StdSchedulerFactory();
        Scheduler scheduler = null;
        try {
            // 通过schedulerFactory获取一个调度器
            scheduler = schedulerfactory.getScheduler();
            // 创建jobDetail实例，绑定Job实现类
            // 指明job的名称，所在组的名称，以及绑定job类
            JobDetail job = JobBuilder.newJob(MyJob.class)
                    .withIdentity("11", "22").build();
            // 定义调度触发规则
            //使用simpleTrigger规则
            Trigger
                    trigger=TriggerBuilder.newTrigger().withIdentity("33",
                    "44")
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(10).withRepeatCount(3))
                    .build();

//            JobDetail job2 = JobBuilder.newJob(MyJob2.class)
//                    .withIdentity("55", "666").build();
//            // 定义调度触发规则
//            //使用simpleTrigger规则
//            Trigger
//                    trigger2=TriggerBuilder.newTrigger().withIdentity("777",
//                    "88")
//                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(8))
//                    .startNow().build();




            // 把作业和触发器注册到任务调度中
            scheduler.scheduleJob(job, trigger);
//            scheduler.scheduleJob(job2, trigger2);
            // 启动调度
            scheduler.start();
        } catch (Exception e) {
            _logger.warn("执行"+111+"组"+222+"任务出现异常E:["+ e.getMessage() + "]");
        }

//        Thread.sleep(40000);
    }
}