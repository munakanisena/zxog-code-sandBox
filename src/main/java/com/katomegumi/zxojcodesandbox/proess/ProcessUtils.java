package com.katomegumi.zxojcodesandbox.proess;

import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author : 惠
 * @description : 获取信息 工具类
 * @createDate : 2025/4/19 下午4:53
 */
public class ProcessUtils {

    /**
     *
     * @param runProcess
     * @param opName 是编译还是执行？
     * @return
     */
    public static ExecuteMessage  runCodeAndGedMessage(Process runProcess,String opName){
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();

            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                System.out.println(opName+"执行成功");
                InputStream inputStream = runProcess.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String Line;
                StringBuilder successStringBuffer = new StringBuilder();
                while ((Line = bufferedReader.readLine()) != null) {
                    successStringBuffer.append(Line).append("\n");
                }
                executeMessage.setMessage(successStringBuffer.toString());
            }else{
                System.out.println(opName+"执行失败 错误码:" + exitValue);
                InputStream inputStream = runProcess.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                String Line;
                StringBuilder successStringBuffer = new StringBuilder();
                while ((Line = bufferedReader.readLine()) != null) {
                    successStringBuffer.append(Line).append("\n");
                }
                executeMessage.setMessage(successStringBuffer.toString());

                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                String errorLine;
                StringBuilder errorStringBuffer = new StringBuilder();
                while ((errorLine = errorBufferedReader.readLine()) != null) {
                    errorStringBuffer.append(errorLine).append("\n");
                }
                executeMessage.setErrorMessage(errorStringBuffer.toString());
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return executeMessage;
    }
}

