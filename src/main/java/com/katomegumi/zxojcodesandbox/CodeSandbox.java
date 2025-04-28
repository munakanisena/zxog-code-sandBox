package com.katomegumi.zxojcodesandbox;


import com.katomegumi.zxojcodesandbox.model.ExecuteCodeRequest;
import com.katomegumi.zxojcodesandbox.model.ExecuteCodeResponse;

public interface CodeSandbox {

    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
