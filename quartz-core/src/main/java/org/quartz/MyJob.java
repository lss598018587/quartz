package org.quartz;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Auther: miaomiao
 * @Date: 2019-03-23 11:57
 * @Description:
 */
public class MyJob implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        System.out.println("帅气的喵喵喵"+format.format(new Date()));
    }
}
