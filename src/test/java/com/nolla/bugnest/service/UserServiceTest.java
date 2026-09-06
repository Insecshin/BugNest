package com.nolla.bugnest.service;

import com.nolla.bugnest.model.User;
import com.nolla.bugnest.repository.MemoryUserRepository;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class UserServiceTest {
    @Test
    void shouldCreateUserWithValidUsername(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        User user = service.createUser("Noah");

        assertEquals("Noah", user.getUsername());
        assertEquals(1L, user.getId());
    }

    @Test
    void shouldRejectEmptyUsername(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, ()->service.createUser(""));
    }

    @Test
    void shouldRejectBlankUsername(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.createUser("   "));
    }

    @Test
    void shouldRejectNullUsername(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        assertThrows(IllegalArgumentException.class, () -> service.createUser(null));
    }

    @Test
    void shouldGetExistingUser(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        User created = service.createUser("Noah");

        User found = service.getUser(created.getId());

        assertEquals("Noah", found.getUsername());
    }

    @Test
    void shouldThrowWhenUserDoesNotExist(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        assertThrows(
                NoSuchElementException.class,
                () -> service.getUser(999L)
        );
    }

    @Test
    void shouldDeleteExistingUser(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        User user = service.createUser("Noah");

        service.deleteUser(user.getId());

        assertThrows(
                NoSuchElementException.class,
                () -> service.getUser(user.getId())
        );
    }

    @Test
    void shouldThrowWhenDeletingNonExistingUser(){
        MemoryUserRepository repository = new MemoryUserRepository();
        UserService service = new UserService(repository);

        assertThrows(
                NoSuchElementException.class,
                () -> service.deleteUser(999L)
        );
    }
}
