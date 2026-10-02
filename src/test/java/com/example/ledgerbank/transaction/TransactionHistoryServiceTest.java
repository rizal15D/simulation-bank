package com.example.ledgerbank.transaction;

import com.example.ledgerbank.account.Account;
import com.example.ledgerbank.account.AccountRepository;
import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.UserRole;
import com.example.ledgerbank.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionHistoryServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionHistoryService service = new TransactionHistoryService(accounts, transactions);
    private final UUID accountId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final BankingPrincipal actor = new BankingPrincipal(
            UUID.randomUUID(), customerId, "owner@example.com", UserRole.CUSTOMER);

    @Test
    void mapsPageAndRequestsDeterministicLatestFirstOrdering() {
        Account account = new Account(customerId, "LBK-2026-00000001");
        ReflectionTestUtils.setField(account, "id", accountId);
        AccountTransaction row = new AccountTransaction(account, null, TransactionType.DEPOSIT,
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        PageRequest request = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(accounts.findById(accountId)).thenReturn(Optional.of(account));
        when(transactions.findByAccountId(accountId, request)).thenReturn(new PageImpl<>(List.of(row), request, 3));

        TransactionHistoryResponse result = service.history(actor, accountId, 1, 1);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.content()).extracting(TransactionResponse::id).containsExactly(row.getId());
    }

    @Test
    void rejectsMissingAccount() {
        assertThatThrownBy(() -> service.history(actor, accountId, 0, 20))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(transactions);
    }

    @Test
    void rejectsInvalidPaginationBeforeQuerying() {
        for (int[] values : new int[][]{{-1, 20}, {0, 0}, {0, -1}, {0, 101}}) {
            assertThatThrownBy(() -> service.history(actor, accountId, values[0], values[1]))
                    .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("INVALID_PAGINATION");
        }
        verifyNoInteractions(accounts, transactions);
    }
}
