package com.katomegumi.zxojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeRequest;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeResponse;
import com.katomegumi.zxojcodesandbox.proess.ExecuteMessage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate{
    private static final long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = true;
    //测试
    public static void main(String[] args) {
        JavaDockerCodeSandboxOld javaNativeCodeSandbox = new JavaDockerCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setInputList(Arrays.asList("1 2","4 5"));
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        //3.创建容器
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String img="openjdk:8-alpine";
        if (FIRST_INIT){
            //拉取镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(img);
            //回调函数
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println(item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取容器失败");
                throw new RuntimeException(e);
            }
        }

        System.out.println("下载完成了");

        //创建容器
        //进行配置
        HostConfig hostConfig = new HostConfig();
        //进行数据挂载
        hostConfig.setBinds(new Bind(userCodeParentPath,new Volume("/app")));
        hostConfig.withMemory(100*1000*1000L);
        hostConfig.withCpuCount(1L);
        //linux 内核安全管理
        String profile = ResourceUtil.readStr("/profile.json",StandardCharsets.UTF_8);

        hostConfig.withSecurityOpts(Arrays.asList("seccomp="+profile));
        CreateContainerResponse exec = dockerClient.createContainerCmd(img)
                .withHostConfig(hostConfig)
                //禁用root写
                .withReadonlyRootfs(false)
                //禁用网络配置
                .withNetworkDisabled(false)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                //交互容器
                .withTty(true)
                .exec();
        String containerId = exec.getId();
        System.out.println(exec);

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //每次的执行结果
        List<ExecuteMessage> executeMessageList=new ArrayList<>();

        //4. 执行 并未获取结果
        for (String inputArgs :inputList) {
            //开始计时
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray=inputArgs.split(" ");
            String[] inputCmd = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);

            //4.1 创建一个命令行
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient
                    .execCreateCmd(containerId)
                    .withCmd(inputCmd)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();

            System.out.println("创建执行命令"+execCreateCmdResponse);

            String execCmdId = execCreateCmdResponse.getId();

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;

            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType frameType = frame.getStreamType();
                    if (StreamType.STDERR.equals(frameType)) {
                        errorMessage[0]=new String(frame.getPayload());
                    } else {
                        message[0]=new String(frame.getPayload());
                    }
                }
            };
            //计算内存
            //4.2 监控(命令行状态)
            final long[] maxMemory={0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);

            statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用"+statistics.getMemoryStats().getUsage());
                    maxMemory[0]= Math.max(statistics.getMemoryStats().getUsage(),maxMemory[0]);
                }
                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            try {
                //4.3 开始计时
                stopWatch.start();
                //4.4 执行命令行
                dockerClient.execStartCmd(execCmdId)
                        .exec(execStartResultCallback)
                        //等待5秒后退出(不管有没有执行完毕)
                        .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                stopWatch.stop();
                time=stopWatch.getLastTaskTimeMillis();
                //4.5 关闭监控
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }

            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setMessage(message[0]);
            executeMessage.setTime(time);
            executeMessage.setMaxMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
    }
        return executeMessageList;
    }
}
