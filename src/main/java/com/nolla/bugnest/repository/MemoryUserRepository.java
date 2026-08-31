package com.nolla.bugnest.repository;

import com.nolla.bugnest.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryUserRepository implements UserRepository{
    private final Map<Long, User> users = new HashMap<>();
    private long nextId = 1L;

    @Override
    public User findById(Long id){
        return users.get(id);
    }

    @Override
    public User save(User user){
        user.assignId(nextId++);
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public List<User> findAll(){
        return new ArrayList<>(users.values());
    }

    @Override
    public void deleteById(Long id){
        users.remove(id);
    }
}
