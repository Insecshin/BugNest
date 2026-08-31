package com.nolla.bugnest.model;

public class User {
    private Long id;
    private String username;

    public User(String username){
        this.username = username;
    }

    public long getId(){
        return id;
    }

    public String getUsername(){
        return username;
    }

    public void assignId(Long id){
        if(this.id != null){
            throw new IllegalStateException("User already has an id");
        }

        if(id == null || id <= 0){
            throw new IllegalArgumentException("Id must be positive");
        }

        this.id = id;
    }
}
