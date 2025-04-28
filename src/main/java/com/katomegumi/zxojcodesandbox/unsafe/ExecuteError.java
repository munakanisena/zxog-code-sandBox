package com.katomegumi.zxojcodesandbox.unsafe;

/**
 * @author : 惠
 * @description : 占用资源
 * @createDate : 2025/4/20 下午12:49
 */
public class ExecuteError {
    public static void main(String[] args) throws Exception {
        long time=60*60*1000L;
        Thread.sleep(time);
        System.out.println("休眠一小时");
    }
}

