package com.nolla.bugnest.repository;

import com.nolla.bugnest.exception.AccountConflictException;
import com.nolla.bugnest.model.Account;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcAccountRepository implements AccountRepository {
    private static final String ACCOUNT_COLUMNS =
            "user_id, user_name, email, password_hash, nickname, created_at";

    private static final RowMapper<Account> ACCOUNT_ROW_MAPPER = (resultSet, rowNum) -> new Account(
            resultSet.getLong("user_id"),
            resultSet.getString("user_name"),
            resultSet.getString("email"),
            resultSet.getString("password_hash"),
            resultSet.getString("nickname"),
            resultSet.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Account save(Account account) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    INSERT INTO public.app_users (user_name, email, password_hash, nickname)
                    VALUES (?, ?, ?, ?)
                    RETURNING user_id, user_name, email, password_hash, nickname, created_at
                    """,
                    ACCOUNT_ROW_MAPPER,
                    account.getUserName(),
                    account.getEmail(),
                    account.getPasswordHash(),
                    account.getNickname()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException("Email or user name already exists", exception);
        }
    }

    @Override
    public Optional<Account> findById(Long id) {
        return jdbcTemplate.query(
                "SELECT " + ACCOUNT_COLUMNS + " FROM public.app_users WHERE user_id = ?",
                ACCOUNT_ROW_MAPPER,
                id
        ).stream().findFirst();
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT " + ACCOUNT_COLUMNS + " FROM public.app_users WHERE email = ?",
                ACCOUNT_ROW_MAPPER,
                email
        ).stream().findFirst();
    }

    @Override
    public boolean existsByUserName(String userName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.app_users WHERE user_name = ?",
                Integer.class,
                userName
        );
        return count != null && count > 0;
    }

    @Override
    public Account updateNickname(Long id, String nickname) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    UPDATE public.app_users
                    SET nickname = ?
                    WHERE user_id = ?
                    RETURNING user_id, user_name, email, password_hash, nickname, created_at
                    """,
                    ACCOUNT_ROW_MAPPER,
                    nickname,
                    id
            );
        } catch (DataIntegrityViolationException exception) {
            throw new AccountConflictException("Nickname is invalid", exception);
        }
    }
}
