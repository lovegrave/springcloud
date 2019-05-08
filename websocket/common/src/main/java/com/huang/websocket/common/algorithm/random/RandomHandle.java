package com.huang.websocket.common.algorithm.random;

import com.huang.websocket.common.algorithm.RouteHandle;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Function: 路由策略， 随机
 *
 * @Auther: jiangyunxiong
 * @Date: 2019/3/7 11:56
 * @since JDK 1.8
 */
public class RandomHandle implements RouteHandle {

    @Override
    public String routeServer(List<String> values, String key) {
        int size = values.size();
        if (size == 0) {
            throw new RuntimeException("CIM 服务器可用服务列表为空");
        }
        int offset = ThreadLocalRandom.current().nextInt(size);

        return values.get(offset);
    }
}
