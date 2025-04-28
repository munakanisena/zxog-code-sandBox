package com.katomegumi.zxojcodesandbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeRequest;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeResponse;
import com.katomegumi.zxojcodesandbox.model.JudgeInfo;
import com.katomegumi.zxojcodesandbox.proess.ExecuteMessage;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.katomegumi.zxojcodesandbox.proess.ProcessUtils.runCodeAndGedMessage;

/**
 * @author : 惠
 * @description : java代码沙箱
 * @createDate : 2025/4/19 下午4:32
 */
public class JavaDockerCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME="tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME="Main.java";

    private static final long Time_OUT = 5000L;


    private static final String SECURITY_MANAGER_NAME="MySecurityManager";

    private static final String SECURITY_MANAGER_PATH="D:\\code\\zxoj-code-sandbox\\src\\main\\resources\\security";

    /**
     * 是否第一次创建容器
     */
    private static final Boolean FIRST_START=true;


    //测试
    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandbox = new JavaDockerCodeSandbox();
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
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();


        //1.写文件 通过UUID 每个用户相互隔离
        //拿到项目路径
        String userDir = System.getProperty("user.dir");
        //整个存放代码目录
        String globalCodePathName= userDir+ File.separator+GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
         FileUtil.mkdir(globalCodePathName);
        }
        //存放代码文件的路径
        String userCodeParentPath  = globalCodePathName+File.separator+ UUID.randomUUID();
        //代码文件名
        String userCodePath = userCodeParentPath+File.separator+GLOBAL_JAVA_CLASS_NAME;
        File userFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);


        //2. 编译文件
        //执行编译命令 并且获取信息
        String compileCmd = String.format("javac -encoding utf-8 %s",userFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage compileMessage = runCodeAndGedMessage(compileProcess,"编译");
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        //3.创建容器
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String img="openjdk:8-alpine";
        if (FIRST_START){
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
                    .awaitCompletion(Time_OUT, TimeUnit.SECONDS);
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

        //4.整理文件
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();

        ArrayList<String> outputList = new ArrayList<>();
        //取最大值用于判定是否超时
        long maxTime=0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (errorMessage != null) {
                executeCodeResponse.setMessage(errorMessage);
                //直接设置失败
                executeCodeResponse.setStatus(3);
                break;
            }
            Long time = executeMessage.getTime();
            if (time!=null){
                maxTime=Math.max(maxTime,time);
            }
            outputList.add(executeMessage.getMessage());
        }
        //如果正常执行 设置状态
        if (executeMessageList.size()==outputList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        //judgeInfo.setMessage();

        //judgeInfo.setMemory();内存判断 非常麻烦 后续采用别的实现 第三方库
        judgeInfo.setTime(maxTime);
        //executeCodeResponse.setMessage(); 在判题服务中才使用
        executeCodeResponse.setJudgeInfo(judgeInfo);
        //5.删除文件
        if (FileUtil.exist(userCodeParentPath)) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    /**
     * 错误处理
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 表示代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

}

