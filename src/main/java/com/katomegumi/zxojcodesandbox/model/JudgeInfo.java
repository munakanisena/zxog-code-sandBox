package com.katomegumi.zxojcodesandbox.model;

import lombok.Data;

/**
 * @author : 惠
 * @description :
 * @createDate : 2025/4/18 下午4:39
 */
@Data
public class JudgeInfo {
    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 消耗内存
     */
    private Long memory;

    /**
     * 消耗时间（KB）
     */
    private Long time;
}

