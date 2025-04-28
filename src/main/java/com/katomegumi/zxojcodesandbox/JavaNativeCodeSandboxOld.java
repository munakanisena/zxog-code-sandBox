package com.katomegumi.zxojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.dfa.WordTree;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeRequest;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeResponse;
import com.katomegumi.zxojcodesandbox.model.JudgeInfo;
import com.katomegumi.zxojcodesandbox.proess.ExecuteMessage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.katomegumi.zxojcodesandbox.proess.ProcessUtils.runCodeAndGedMessage;

/**
 * @author : 惠
 * @description : java代码沙箱
 * @createDate : 2025/4/19 下午4:32
 */
public class JavaNativeCodeSandboxOld implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME="tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME="Main.java";

    private static final long Time_OUT = 5000L;

    //黑名单 但是有局限性 因为你无法预料所有的情况 需要java安全管理
    private static final List<String> blackList=Arrays.asList("File","exec");

    private static final String SECURITY_MANAGER_NAME="MySecurityManager";

    private static final String SECURITY_MANAGER_PATH="D:\\code\\zxoj-code-sandbox\\src\\main\\resources\\security";
    //字符字典
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE=new WordTree();
        //添加黑名单
        WORD_TREE.addWords(blackList);
    }

    //测试
    public static void main(String[] args) {
        JavaNativeCodeSandboxOld javaNativeCodeSandboxOld = new JavaNativeCodeSandboxOld();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        //String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
        //String code = ResourceUtil.readStr("unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);
        String code = ResourceUtil.readStr("unsafeCode/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setInputList(Arrays.asList("1 2","4 5"));
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");

        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandboxOld.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);

    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        //System.setSecurityManager(new MySecurityManager());

        //在编译之前校验 提交代码有无危险操作
//        FoundWord foundWord = WORD_TREE.matchWord(code);
//        if (foundWord != null) {
//            System.out.println(foundWord.getWord());
//            return null;
//        }

        //1.写文件 通过UUID 每个用户相互隔离
        //整个存放代母目录
        String userDir = System.getProperty("user.dir");//拿到项目路径
        String globalCodePathName= userDir+ File.separator+GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
         FileUtil.mkdir(globalCodePathName);
        }
        String userCodeParentPath  = globalCodePathName+File.separator+ UUID.randomUUID();
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
        //3.运行文件 并且可以使用命令去限制堆内存
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            //在运行的时候加载securityManager 不影响开发者的使用 %s -Djava.security.manager=%s
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH,SECURITY_MANAGER_NAME,inputArgs);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                //开启守护线程 记时
                new Thread(()->{
                    try {
                        Thread.sleep(Time_OUT);
                        if (process!=null){
                            System.out.println("超时线程删除");
                            process.destroy();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage runMessage = runCodeAndGedMessage(process,"执行");
                System.out.println(runMessage);
                executeMessageList.add(runMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
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

