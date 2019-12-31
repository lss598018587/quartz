package org.quartz;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @Auther: miaomiao
 * @Date: 2019-03-23 11:57
 * @Description:
 */
public class MyJob2 implements Job {
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        System.out.println("xxxxxxxxxxx:"+format.format(new Date()));
    }
}
