package com.nolla.bugnest.repository;

import com.nolla.bugnest.model.Account;

import java.util.Optional;

public interface AccountRepository {
    Account save(Account account);

    Optional<Account> findById(Long id);

    Optional<Account> findByEmail(String email);

    boolean existsByUserName(String userName);

    Account updateNickname(Long id, String nickname);
}
