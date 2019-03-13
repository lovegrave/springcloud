package com.jiangroom.authserver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

import javax.sql.DataSource;

@SpringBootApplication
@EnableResourceServer
@EnableEurekaClient
public class AuthServerApplication {

    @Autowired
    @Qualifier("dataSource")
    private DataSource dataSource;
    public static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }

}
