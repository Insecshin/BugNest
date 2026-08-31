package com.nolla.bugnest.repository;

import com.nolla.bugnest.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MemoryUserRepositoryTest {
    @Test
    void shouldFindUserAfterSaving(){
        MemoryUserRepository repository = new MemoryUserRepository();

        User user = new User("Noah");

        User savedUser = repository.save(user);
        User foundUser = repository.findById(savedUser.getId());

        assertEquals("Noah", foundUser.getUsername());
    }

    @Test
    void shouldFindAllUsers(){
        MemoryUserRepository repository = new MemoryUserRepository();

        repository.save(new User("Noah"));
        repository.save(new User("Alice"));

        List<User> users = repository.findAll();

        assertEquals(2, users.size());
    }

    @Test
    void shouldDeleteUserById(){
        MemoryUserRepository repository = new MemoryUserRepository();

        User user = repository.save(new User("Noah"));

        repository.deleteById(user.getId());

        User foundUser = repository.findById(user.getId());

        assertNull(foundUser);
    }
}
