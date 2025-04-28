

import java.util.ArrayList;

/**
 * @author : 惠
 * @description : 内存溢出
 * @createDate : 2025/4/20 下午12:52
 */
public class Main {
    public static void main(String[] args) {
        ArrayList<Byte[]> bytes = new ArrayList<>();
        while (true) {
            bytes.add(new Byte[1000]);
        }
    }
}

