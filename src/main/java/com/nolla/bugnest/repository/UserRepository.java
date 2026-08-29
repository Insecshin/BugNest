package com.nolla.bugnest.repository;

import com.nolla.bugnest.model.User;

import java.util.List;

public interface UserRepository {
    User save(User user);
    User findById(Long id);
    List<User> findAll();
    void deleteById(Long id);
}
