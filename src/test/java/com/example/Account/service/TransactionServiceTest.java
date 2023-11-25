package com.example.Account.service;

import com.example.Account.domain.Account;
import com.example.Account.domain.AccountUser;
import com.example.Account.domain.Transaction;
import com.example.Account.dto.TransactionDto;
import com.example.Account.exception.AccountException;
import com.example.Account.repository.AccountRepository;
import com.example.Account.repository.AccountUserRespository;
import com.example.Account.repository.TransactionRepository;
import com.example.Account.type.AccountStatus;
import com.example.Account.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.Account.type.AccountStatus.IN_USE;
import static com.example.Account.type.TransactionResultType.F;
import static com.example.Account.type.TransactionResultType.S;
import static com.example.Account.type.TransactionType.USE;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    public static final long USE_AMOUNT = 200L;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock // 가짜로 만들어줌.
    private AccountRepository accountRepository;
    @Mock
    private AccountUserRespository accountUserRespository;

    @InjectMocks // 위에서 만든 mock을 주입시켜줌.
    private TransactionService transactionService;

    @Test
    void successUseBalance(){
        // given
        AccountUser user = AccountUser.builder()
                .name("Pobi")
                .id(12L)
                .build();

        given(accountUserRespository.findById(anyLong()))
                .willReturn(Optional.of(user));
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("100000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionalId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        // when
        TransactionDto transactionDto = transactionService.useBalance(1L,
                "1000000000", USE_AMOUNT);


        // then
        verify(transactionRepository,times(1)).save(captor.capture());
        assertEquals(USE_AMOUNT,captor.getValue().getAmount());
        assertEquals(9800L,captor.getValue().getBalanceSnapshot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapshot());
        assertEquals(1000L, transactionDto.getAmount());

    }

    @Test
    @DisplayName("해당 유저 없음 - 잔액 사용 실패")
    void useBalance_UserNotFound(){
        // given
        given(accountUserRespository.findById(anyLong()))
                .willReturn(Optional.empty());

        // when
        AccountException accountException = assertThrows(AccountException.class,
                ()-> transactionService.useBalance(1L,"1000000000",1000L));
        // then
        assertEquals(ErrorCode.USER_NOT_FOUND,accountException.getErrorCode());

    }
    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void useBalance_AccountNotFound(){
        // given
        AccountUser user = AccountUser.builder()
                .name("Pobi")
                .id(12L)
                .build();
        given(accountUserRespository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        // when
        AccountException accountException = assertThrows(AccountException.class,
                ()-> transactionService.useBalance(1L,"1000000000",USE_AMOUNT));
        // then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND,accountException.getErrorCode());

    }

    @Test
    @DisplayName("사용자와 카드 소유주 불일치")
    void useBalanceFailed_userUnMatch(){
        // given
        AccountUser pobi = AccountUser.builder()
                .name("Pobi")
                .id(12L)
                .build();

        AccountUser harry = AccountUser.builder()
                .name("Harry")
                .id(13L)
                .build();
        given(accountUserRespository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(harry)
                        .balance(0L)
                        .accountNumber("100000012")
                        .build()));

        // when
        AccountException accountException = assertThrows(AccountException.class,
                ()-> transactionService.useBalance(1L,"1234567890",USE_AMOUNT));
        // then
        assertEquals(ErrorCode.USER_ACCOUNT_UN_MATCH,accountException.getErrorCode());

    }

    @Test
    @DisplayName("해지 계좌는 해지할 수 없다.")
    void useBalanceFailed_alreadyUnregistered(){
        // given
        AccountUser pobi = AccountUser.builder()
                .name("Pobi")
                .id(12L)
                .build();
        given(accountUserRespository.findById(anyLong()))
                .willReturn(Optional.of(pobi));

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(pobi)
                        .accountStatus(AccountStatus.UNREGISTERED)
                        .balance(0L)
                        .accountNumber("100000012")
                        .build()));

        // when
        AccountException accountException = assertThrows(AccountException.class,
                ()-> transactionService.useBalance(1L,"1234567890",USE_AMOUNT));

        // then
        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED,accountException.getErrorCode());

    }
    @Test
    @DisplayName("거래 금액이 잔액보다 큰 경우")
    void useBalanceFailed_exceedAmount(){
        // given
        AccountUser user = AccountUser.builder()
                .name("Pobi")
                .id(12L)
                .build();

        given(accountUserRespository.findById(anyLong()))
                .willReturn(Optional.of(user));
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(100L)
                .accountNumber("100000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        // when
        // then
        AccountException accountException = assertThrows(AccountException.class,
                ()-> transactionService.useBalance(1L,"1234567890",USE_AMOUNT));
        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE,accountException.getErrorCode());

        verify(transactionRepository,times(0)).save(any());

    }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailUseTransaction(){
        // given
        AccountUser user = AccountUser.builder()
                .name("Pobi")
                .id(12L)
                .build();

        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(1000L)
                .accountNumber("100000012").build();
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionalId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapshot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        // when
        transactionService.saveFaileUseTransaction("1000000000", USE_AMOUNT);

        // then
        verify(transactionRepository,times(1)).save(captor.capture());
        assertEquals(USE_AMOUNT,captor.getValue().getAmount());
        assertEquals(1000L,captor.getValue().getBalanceSnapshot());
        assertEquals(F, captor.getValue().getTransactionResultType());

    }



}