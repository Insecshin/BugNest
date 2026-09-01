package com.nolla.bugnest.service;

import com.nolla.bugnest.model.User;
import com.nolla.bugnest.repository.UserRepository;

public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    public User createUser(String username){
        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Username must not be blank");
        }

        User user = new User(username);

        return userRepository.save(user);
    }
}
