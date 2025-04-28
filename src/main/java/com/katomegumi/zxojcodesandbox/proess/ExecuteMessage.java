package com.katomegumi.zxojcodesandbox.proess;

import lombok.Data;

/**
 * @author : 惠
 * @description :
 * @createDate : 2025/4/19 下午4:57
 */
@Data
public class ExecuteMessage {
    //成功消息
    private String message;
    //执行码
    private Integer exitValue;
    //错误消息
    private String errorMessage;
    //执行时间
    private Long time;
}

