package com.eventledger.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for calling the Account Service with resilience patterns.
 */
@Component
public class AccountServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AccountServiceClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${account-service.url:http://localhost:8081}")
    private String accountServiceUrl;

    private final RestTemplate restTemplate;

    public AccountServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Apply a transaction to an account with resilience patterns:
     * - Circuit breaker: stops calling if service is failing
     * - Retry: retries on failure with backoff
     * - Time limiter: enforces timeout
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    @Retry(name = "accountService")
    @TimeLimiter(name = "accountService")
    public CompletableFuture<String> applyTransaction(String accountId, String type, BigDecimal amount,
                                                        String currency, String idempotencyKey, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = accountServiceUrl + "/accounts/" + accountId + "/transactions";

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("type", type);
                requestBody.put("amount", amount);
                requestBody.put("currency", currency);
                requestBody.put("idempotencyKey", idempotencyKey);

                logger.info("Calling Account Service: {} [traceId={}]", url, traceId);

                restTemplate.postForObject(url, requestBody, String.class);
                return "SUCCESS";
            } catch (RestClientException e) {
                logger.error("Failed to apply transaction to Account Service [traceId={}]", traceId, e);
                throw new AccountServiceException("Account Service call failed", e);
            }
        });
    }

    /**
     * Fallback method when Account Service is unavailable
     */
    public CompletableFuture<String> applyTransactionFallback(String accountId, String type, BigDecimal amount,
                                                               String currency, String idempotencyKey, String traceId, Exception ex) {
        logger.warn("Circuit breaker fallback triggered for Account Service [traceId={}]: {}", traceId, ex.getMessage());
        return CompletableFuture.failedFuture(
            new AccountServiceUnavailableException("Account Service is currently unavailable", ex)
        );
    }

    /**
     * Get account balance
     */
    @CircuitBreaker(name = "accountService", fallbackMethod = "getBalanceFallback")
    @Retry(name = "accountService")
    public BigDecimal getBalance(String accountId, String traceId) {
        try {
            String url = accountServiceUrl + "/accounts/" + accountId + "/balance";
            logger.info("Fetching balance from Account Service: {} [traceId={}]", url, traceId);

            String response = restTemplate.getForObject(url, String.class);
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            return new BigDecimal(responseMap.get("balance").toString());
        } catch (Exception e) {
            logger.error("Failed to get balance from Account Service [traceId={}]", traceId, e);
            throw new AccountServiceException("Failed to get balance", e);
        }
    }

    /**
     * Fallback for getBalance
     */
    public BigDecimal getBalanceFallback(String accountId, String traceId, Exception ex) {
        logger.warn("Circuit breaker fallback triggered for getBalance [traceId={}]: {}", traceId, ex.getMessage());
        throw new AccountServiceUnavailableException("Account Service is currently unavailable", ex);
    }

    public static class AccountServiceException extends RuntimeException {
        public AccountServiceException(String message) {
            super(message);
        }

        public AccountServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class AccountServiceUnavailableException extends RuntimeException {
        public AccountServiceUnavailableException(String message) {
            super(message);
        }

        public AccountServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

