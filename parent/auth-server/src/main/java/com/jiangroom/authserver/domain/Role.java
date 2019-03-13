package com.jiangroom.authserver.domain;

import org.springframework.security.core.GrantedAuthority;

public class Role implements GrantedAuthority {

    private Long id;

    private  String name;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getAuthority() {
        return name;
    }
}
