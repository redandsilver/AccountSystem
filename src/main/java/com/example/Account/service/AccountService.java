package com.example.Account.service;

import com.example.Account.domain.Account;
import com.example.Account.domain.AccountUser;
import com.example.Account.dto.AccountDto;
import com.example.Account.exception.AccountException;
import com.example.Account.repository.AccountRepository;
import com.example.Account.repository.AccountUserRespository;
import com.example.Account.type.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static com.example.Account.type.AccountStatus.IN_USE;

@Service
@RequiredArgsConstructor // 필수 인자만 입력받는 생성자를 만들어주는 어노테이션
public class AccountService {

    // final: 무조건 생성자에 들어가야하는 값
    private final AccountRepository accountRepository;
    private final AccountUserRespository accountUserRespository;

    /**
     *
     *  사용자가 있는지 조회
     *   계좌 번호 생성
     *   계좌 저장, 그 정보 전달
     */
    @Transactional
    public AccountDto createAccount(Long userId, Long initialBalance){
        AccountUser accountUser = accountUserRespository.findById(userId)
                .orElseThrow(() -> new AccountException(ErrorCode.USER_NOT_FOUND));

        // 계좌가 있다면 가장 최근 만들어진 계좌번호 +1
        // 계좌가 없다면 100000000 으로 만들어줌.
        String newAccountNumber = accountRepository.findFirstByOrderByIdDesc()
                .map(account -> (Integer.parseInt(account.getAccountNumber())+1)+"")
                .orElse("1000000000");

        return AccountDto.fromEntity(accountRepository.save(
                Account.builder()
                        .accountUser(accountUser)
                        .accountStatus(IN_USE)
                        .accountNumber(newAccountNumber)
                        .balance(initialBalance)
                        .registeredAt(LocalDateTime.now())
                        .build()));

    }

    @Transactional
    public Account getAccount(Long id){
        if(id < 0){
            throw new RuntimeException("Minus");
        }
        return accountRepository.findById(id).get();
    }
}