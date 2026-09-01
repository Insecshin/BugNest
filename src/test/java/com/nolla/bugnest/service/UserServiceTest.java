package com.nolla.bugnest.service;

import com.nolla.bugnest.model.User;
import com.nolla.bugnest.repository.MemoryUserRepository;
import org.junit.jupiter.api.Test;

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
}
