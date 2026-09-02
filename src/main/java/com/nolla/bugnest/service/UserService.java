package com.nolla.bugnest.service;

import com.nolla.bugnest.model.User;
import com.nolla.bugnest.repository.UserRepository;

import java.util.List;
import java.util.NoSuchElementException;

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

    public User getUser(Long id){
        User user = userRepository.findById(id);

        if(user == null){
            throw new NoSuchElementException("User not found");
        }

        return user;
    }

    public List<User> getALlUsers(){
        return userRepository.findAll();
    }

    public void deleteUser(Long id){
        User user = userRepository.findById(id);

        if(user == null){
            throw new NoSuchElementException("User not found");
        }

        userRepository.deleteById(id);
    }
}
