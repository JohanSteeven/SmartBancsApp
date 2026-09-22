package com.financial.platform.transaction.api;

import com.financial.platform.transaction.application.AccountService;
import com.financial.platform.transaction.domain.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Endpoints para consulta de cuentas bancarias y saldos")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar saldo e información de una cuenta por ID")
    public AccountResponse getAccount(@PathVariable UUID id) {
        Account account = accountService.getAccountById(id);
        return AccountResponse.from(account);
    }
}
