package com.eventledger.account.controller;

import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class AccountControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    @Captor
    private ArgumentCaptor<String> stringCaptor;

    @Captor
    private ArgumentCaptor<TransactionRequest> transactionRequestCaptor;

    private Account testAccount;
    private Transaction testTransaction;
    private TransactionRequest testTransactionRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        objectMapper = new ObjectMapper();
        testAccount = new Account("acct-123");
        testAccount.setBalance(new BigDecimal("100.00"));
        testAccount.setCreatedAt(Instant.now());
        testAccount.setUpdatedAt(Instant.now());

        testTransaction = new Transaction("acct-123", Transaction.TransactionType.CREDIT,
            new BigDecimal("50.00"), "USD", "idem-key-1");
        testTransaction.setCreatedAt(Instant.now());

        testTransactionRequest = new TransactionRequest("CREDIT", new BigDecimal("50.00"), "USD", "idem-key-1");
    }



    @Test
    void applyTransaction_withValidTraceId_returns200() throws Exception {
        String accountId = "acct-123";

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", "trace-123")
                .content(objectMapper.writeValueAsString(testTransactionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.traceId").value("trace-123"));

        verify(accountService, times(1)).applyTransaction(eq(accountId), any(TransactionRequest.class), eq("trace-123"));
    }

    @Test
    void applyTransaction_withoutTraceId_generatesTraceId() throws Exception {
        String accountId = "acct-456";

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testTransactionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.traceId").exists());

        verify(accountService, times(1)).applyTransaction(eq(accountId), any(TransactionRequest.class), anyString());
    }

    @Test
    void applyTransaction_withEmptyTraceId_generatesNewTraceId() throws Exception {
        String accountId = "acct-789";

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", "")
                .content(objectMapper.writeValueAsString(testTransactionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.traceId").exists());

        verify(accountService, times(1)).applyTransaction(eq(accountId), any(TransactionRequest.class), anyString());
    }

    @Test
    void applyTransaction_transactionApplicationException_returns500() throws Exception {
        String accountId = "acct-error";
        String traceId = "trace-error";

        doThrow(new AccountService.TransactionApplicationException("Transaction failed", new RuntimeException("DB error")))
                .when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", traceId)
                .content(objectMapper.writeValueAsString(testTransactionRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Transaction failed"))
                .andExpect(jsonPath("$.message").value("Transaction failed"))
                .andExpect(jsonPath("$.traceId").value(traceId));

        verify(accountService, times(1)).applyTransaction(eq(accountId), any(TransactionRequest.class), eq(traceId));
    }

    @Test
    void applyTransaction_unexpectedException_returns500() throws Exception {
        String accountId = "acct-unexpected";
        String traceId = "trace-unexpected";

        doThrow(new RuntimeException("Unexpected DB error"))
                .when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", traceId)
                .content(objectMapper.writeValueAsString(testTransactionRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("Unexpected DB error"))
                .andExpect(jsonPath("$.traceId").value(traceId));

        verify(accountService, times(1)).applyTransaction(eq(accountId), any(TransactionRequest.class), eq(traceId));
    }

    @Test
    void applyTransaction_withCreditTransaction() throws Exception {
        String accountId = "acct-credit";
        TransactionRequest creditRequest = new TransactionRequest("CREDIT", new BigDecimal("100.00"), "USD", "idem-credit");

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", "trace-credit")
                .content(objectMapper.writeValueAsString(creditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(accountService).applyTransaction(eq(accountId), any(TransactionRequest.class), anyString());
    }

    @Test
    void applyTransaction_withDebitTransaction() throws Exception {
        String accountId = "acct-debit";
        TransactionRequest debitRequest = new TransactionRequest("DEBIT", new BigDecimal("50.00"), "USD", "idem-debit");

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", "trace-debit")
                .content(objectMapper.writeValueAsString(debitRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(accountService).applyTransaction(eq(accountId), any(TransactionRequest.class), anyString());
    }


    @Test
    void getBalance_withValidTraceId_returns200() throws Exception {
        String accountId = "acct-balance";
        BigDecimal balance = new BigDecimal("250.50");

        when(accountService.getBalance(anyString(), anyString())).thenReturn(balance);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-ID", "trace-balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balance").value(250.50))
                .andExpect(jsonPath("$.traceId").value("trace-balance"));

        verify(accountService, times(1)).getBalance(eq(accountId), eq("trace-balance"));
    }

    @Test
    void getBalance_withoutTraceId_generatesTraceId() throws Exception {
        String accountId = "acct-balance-notrace";
        BigDecimal balance = new BigDecimal("100.00");

        when(accountService.getBalance(anyString(), anyString())).thenReturn(balance);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.traceId").exists());

        verify(accountService).getBalance(eq(accountId), anyString());
    }

    @Test
    void getBalance_withEmptyTraceId_generatesNewTraceId() throws Exception {
        String accountId = "acct-balance-empty";
        BigDecimal balance = new BigDecimal("50.00");

        when(accountService.getBalance(anyString(), anyString())).thenReturn(balance);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-ID", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00))
                .andExpect(jsonPath("$.traceId").exists());

        verify(accountService).getBalance(eq(accountId), anyString());
    }

    @Test
    void getBalance_exception_returns500() throws Exception {
        String accountId = "acct-balance-error";
        String traceId = "trace-balance-error";

        when(accountService.getBalance(anyString(), anyString()))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-ID", traceId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("Database error"))
                .andExpect(jsonPath("$.traceId").value(traceId));

        verify(accountService).getBalance(eq(accountId), eq(traceId));
    }

    @Test
    void getBalance_zeroBalance_returns200() throws Exception {
        String accountId = "acct-zero";

        when(accountService.getBalance(anyString(), anyString())).thenReturn(BigDecimal.ZERO);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-ID", "trace-zero"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));

        verify(accountService).getBalance(eq(accountId), eq("trace-zero"));
    }


    @Test
    void getAccount_withValidTraceId_returns200() throws Exception {
        String accountId = "acct-123";

        when(accountService.getAccount(anyString(), anyString())).thenReturn(testAccount);
        when(accountService.getTransactions(anyString(), anyString())).thenReturn(List.of(testTransaction));

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", "trace-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        verify(accountService).getAccount(eq(accountId), eq("trace-123"));
        verify(accountService).getTransactions(eq(accountId), eq("trace-123"));
    }

    @Test
    void getAccount_withoutTraceId_generatesTraceId() throws Exception {
        String accountId = "acct-456";
        Account account = new Account(accountId);
        account.setBalance(new BigDecimal("100.00"));
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());

        when(accountService.getAccount(anyString(), anyString())).thenReturn(account);
        when(accountService.getTransactions(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/accounts/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.transactionCount").value(0));

        verify(accountService).getAccount(eq(accountId), anyString());
        verify(accountService).getTransactions(eq(accountId), anyString());
    }

    @Test
    void getAccount_withEmptyTraceId_generatesNewTraceId() throws Exception {
        String accountId = "acct-789";

        when(accountService.getAccount(anyString(), anyString())).thenReturn(testAccount);
        when(accountService.getTransactions(anyString(), anyString())).thenReturn(List.of(testTransaction, testTransaction));

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.transactionCount").value(2));

        verify(accountService).getAccount(eq(accountId), anyString());
    }

    @Test
    void getAccount_accountNotFound_returns404() throws Exception {
        String accountId = "acct-notfound";
        String traceId = "trace-404";

        when(accountService.getAccount(anyString(), anyString()))
                .thenThrow(new AccountService.AccountNotFoundException("Account not found: " + accountId));

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", traceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found"))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.traceId").value(traceId));

        verify(accountService).getAccount(eq(accountId), eq(traceId));
    }

    @Test
    void getAccount_unexpectedException_returns500() throws Exception {
        String accountId = "acct-error";
        String traceId = "trace-error";

        when(accountService.getAccount(anyString(), anyString()))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", traceId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"))
                .andExpect(jsonPath("$.message").value("Database error"))
                .andExpect(jsonPath("$.traceId").value(traceId));

        verify(accountService).getAccount(eq(accountId), eq(traceId));
    }

    @Test
    void getAccount_noTransactions_returns200() throws Exception {
        String accountId = "acct-no-tx";

        when(accountService.getAccount(anyString(), anyString())).thenReturn(testAccount);
        when(accountService.getTransactions(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", "trace-no-tx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(0));

        verify(accountService).getTransactions(eq(accountId), eq("trace-no-tx"));
    }

    @Test
    void getAccount_multipleTransactions_returns200() throws Exception {
        String accountId = "acct-multi-tx";
        Transaction tx1 = new Transaction(accountId, Transaction.TransactionType.CREDIT, new BigDecimal("50"), "USD", "idem-1");
        Transaction tx2 = new Transaction(accountId, Transaction.TransactionType.DEBIT, new BigDecimal("20"), "USD", "idem-2");
        Transaction tx3 = new Transaction(accountId, Transaction.TransactionType.CREDIT, new BigDecimal("30"), "USD", "idem-3");

        when(accountService.getAccount(anyString(), anyString())).thenReturn(testAccount);
        when(accountService.getTransactions(anyString(), anyString())).thenReturn(List.of(tx1, tx2, tx3));

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", "trace-multi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionCount").value(3));

        verify(accountService).getTransactions(eq(accountId), eq("trace-multi"));
    }

    // =========================
    // health Tests
    // =========================

    @Test
    void health_returns200() throws Exception {
        mockMvc.perform(get("/accounts/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("account-service"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void health_doesNotCallService() throws Exception {
        mockMvc.perform(get("/accounts/health"))
                .andExpect(status().isOk());

        verify(accountService, never()).getAccount(anyString(), anyString());
        verify(accountService, never()).getBalance(anyString(), anyString());
        verify(accountService, never()).applyTransaction(anyString(), any(), anyString());
    }


    @Test
    void applyTransaction_withoutTraceIdHeader_shouldGenerateNewTraceId() throws Exception {
        String accountId = "acct-null-trace";

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testTransactionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        verify(accountService).applyTransaction(eq(accountId), any(TransactionRequest.class), anyString());
    }

    @Test
    void getBalance_largeBalance_returns200() throws Exception {
        String accountId = "acct-large";
        BigDecimal largeBalance = new BigDecimal("999999999.99");

        when(accountService.getBalance(anyString(), anyString())).thenReturn(largeBalance);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-ID", "trace-large"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", closeTo(999999999.99, 0.01)));

        verify(accountService).getBalance(eq(accountId), eq("trace-large"));
    }

    @Test
    void getBalance_negativeBalance_returns200() throws Exception {
        String accountId = "acct-negative";
        BigDecimal negativeBalance = new BigDecimal("-500.00");

        when(accountService.getBalance(anyString(), anyString())).thenReturn(negativeBalance);

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId)
                .header("X-Trace-ID", "trace-negative"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(-500.00));

        verify(accountService).getBalance(eq(accountId), eq("trace-negative"));
    }

    @Test
    void applyTransaction_verifyRequestIsPassedCorrectly() throws Exception {
        String accountId = "acct-verify";
        BigDecimal amount = new BigDecimal("123.45");
        String type = "CREDIT";
        String currency = "USD";
        String idempotencyKey = "idem-verify";

        TransactionRequest request = new TransactionRequest(type, amount, currency, idempotencyKey);

        doNothing().when(accountService).applyTransaction(anyString(), any(TransactionRequest.class), anyString());

        mockMvc.perform(post("/accounts/{accountId}/transactions", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-ID", "trace-verify")
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(accountService).applyTransaction(
                eq(accountId),
                argThat(req -> req.getType().equals(type) &&
                              req.getAmount().compareTo(amount) == 0 &&
                              req.getCurrency().equals(currency) &&
                              req.getIdempotencyKey().equals(idempotencyKey)),
                eq("trace-verify")
        );
    }

    @Test
    void getAccount_verifyTimestampsInResponse() throws Exception {
        String accountId = "acct-timestamps";
        Instant now = Instant.now();

        Account account = new Account(accountId);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        when(accountService.getAccount(anyString(), anyString())).thenReturn(account);
        when(accountService.getTransactions(anyString(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/accounts/{accountId}", accountId)
                .header("X-Trace-ID", "trace-ts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        verify(accountService).getAccount(eq(accountId), eq("trace-ts"));
    }
}

