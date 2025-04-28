package com.katomegumi.zxojcodesandbox.unsafe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author : 惠
 * @description : 阅读系统文件
 * @createDate : 2025/4/20 下午12:01
 */
public class ReadFileError {
    public static void main(String[] args) throws IOException {
        String userDir = System.getProperty("user.dir");
        String filePath=userDir+ File.separator+"src/main/resources/application.yml";
        List<String> readAllLines = Files.readAllLines(Paths.get(filePath));
        System.out.println(String.join("\n", readAllLines));
    }
}

