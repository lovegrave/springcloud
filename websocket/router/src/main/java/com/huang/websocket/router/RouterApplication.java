package com.huang.websocket.router;

import com.huang.websocket.router.kit.ServerListListener;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RouterApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(RouterApplication.class, args);
    }


    @Override
    public void run(String... args) throws Exception {

        //监听服务
        Thread thread = new Thread(new ServerListListener());
        thread.setName("zk-listener");
        thread.start() ;
    }

}
