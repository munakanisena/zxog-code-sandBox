package com.katomegumi.zxojcodesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;


public class dockerDemo {
    public static void main(String[] args) throws InterruptedException {
        //docker 客户端
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
//        PingCmd pingCmd = dockerClient.pingCmd();
//        pingCmd.exec();

        //拉取镜像
        String img="nginx:latest";
//        PullImageCmd pullImageCmd = dockerClient.pullImageCmd(img);
//        //回调函数
//        PullImageResultCallback pullImageResultCallback = new PullImageResultCallback(){
//            @Override
//            public void onNext(PullResponseItem item) {
//                System.out.println(item.getStatus());
//                super.onNext(item);
//            }
//        };
//        pullImageCmd.exec(pullImageResultCallback)
//                    .awaitCompletion();
//        System.out.println("下载完成了");

        //创建容器
        CreateContainerResponse exec = dockerClient.createContainerCmd(img).
                withCmd("ech","Hello Docker")
                .exec();
        String containerId = exec.getId();
        System.out.println(exec);

        //查看容器信息
        List<Container> containerList = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();
        for (Container container :containerList) {
            System.out.println(container);
        }

        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //查看日志
        LogContainerResultCallback logContainerResultCallback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                System.out.println("日志：" + new String(item.getPayload()));
                super.onNext(item);
            }
        };
        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .exec(logContainerResultCallback)
                .awaitCompletion();

        //删除容器(强制删除)
        dockerClient.removeContainerCmd(containerId).withForce(true).exec();

        //删除镜像
        dockerClient.removeImageCmd(img).exec();

    }
}
