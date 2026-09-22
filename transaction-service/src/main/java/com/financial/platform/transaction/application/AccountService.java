package com.financial.platform.transaction.application;

import com.financial.platform.transaction.domain.Account;
import com.financial.platform.transaction.domain.AccountNotFoundException;
import com.financial.platform.transaction.infrastructure.AccountJdbcRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountJdbcRepository accountRepository;

    public AccountService(AccountJdbcRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account getAccountById(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
