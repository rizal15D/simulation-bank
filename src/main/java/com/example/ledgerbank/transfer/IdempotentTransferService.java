package com.example.ledgerbank.transfer;

import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.common.Money;
import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.common.observability.BankingMetrics;
import com.example.ledgerbank.event.BankingEvent;
import com.example.ledgerbank.event.BankingEventPublisher;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IdempotentTransferService {
    private static final Logger log = LoggerFactory.getLogger(IdempotentTransferService.class);
    private static final String KEY_PATTERN = "[A-Za-z0-9._:-]{8,128}";

    private final RedisTransferIdempotencyStore redis;
    private final TransferIdempotencyRepository idempotencyRecords;
    private final TransferRepository transfers;
    private final TransferService transferService;
    private final TransactionTemplate transaction;
    private final BankingEventPublisher events;
    private final BankingMetrics metrics;
    private final Duration resultTtl;
    private final Duration lockTtl;

    public IdempotentTransferService(RedisTransferIdempotencyStore redis,
                                     TransferIdempotencyRepository idempotencyRecords,
                                     TransferRepository transfers, TransferService transferService,
                                     TransactionTemplate transaction, BankingEventPublisher events,
                                     BankingMetrics metrics,
                                     @Value("${ledgerbank.idempotency.transfer-ttl}") Duration resultTtl,
                                     @Value("${ledgerbank.idempotency.lock-ttl}") Duration lockTtl) {
        this.redis = redis;
        this.idempotencyRecords = idempotencyRecords;
        this.transfers = transfers;
        this.transferService = transferService;
        this.transaction = transaction;
        this.events = events;
        this.metrics = metrics;
        this.resultTtl = resultTtl;
        this.lockTtl = lockTtl;
    }

    public TransferResponse transfer(BankingPrincipal actor, String idempotencyKey, TransferRequest request) {
        var sample = metrics.startTransfer();
        RuntimeException failure = null;
        try {
            return executeTransfer(actor, idempotencyKey, request);
        } catch (RuntimeException caught) {
            failure = caught;
            throw caught;
        } finally {
            metrics.completeTransfer(sample, failure);
        }
    }

    private TransferResponse executeTransfer(BankingPrincipal actor, String idempotencyKey, TransferRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches(KEY_PATTERN)) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 128 letters, digits, dot, underscore, colon, or hyphen",
                    HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount = Money.requireValid(request.amount());
        String requestHash = fingerprint(request.sourceAccountId(), request.destinationAccountId(), amount,
                request.description());
        try {
            TransferResponse cached = cached(actor.userId(), idempotencyKey, requestHash);
            if (cached != null) {
                return cached;
            }
        } catch (DataAccessException unavailable) {
            throw unavailable();
        }

        String lockOwner = UUID.randomUUID().toString();
        try {
            if (!redis.acquire(actor.userId(), idempotencyKey, lockOwner, lockTtl)) {
                TransferResponse committed = fromDatabase(actor.userId(), idempotencyKey, requestHash);
                if (committed != null) {
                    return committed;
                }
                throw new BusinessException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        "A transfer with this Idempotency-Key is still in progress", HttpStatus.CONFLICT);
            }
        } catch (DataAccessException unavailable) {
            throw unavailable();
        }

        try {
            TransferResponse result;
            try {
                result = transaction.execute(status -> {
                    TransferResponse existing = fromDatabase(actor.userId(), idempotencyKey, requestHash);
                    if (existing != null) {
                        return existing;
                    }
                    TransferResponse created = transferService.transfer(actor, request.sourceAccountId(),
                            request.destinationAccountId(), amount, request.description());
                    idempotencyRecords.saveAndFlush(new TransferIdempotency(actor.userId(), idempotencyKey,
                            requestHash, created.id(), Instant.now().plus(resultTtl)));
                    return created;
                });
            } catch (RuntimeException failure) {
                String failureCode = failure instanceof BusinessException business
                        ? business.getCode() : "TRANSFER_PROCESSING_FAILED";
                events.publish(BankingEvent.transferFailed(actor.userId(), request.sourceAccountId(),
                        request.destinationAccountId(), failureCode));
                throw failure;
            }
            try {
                redis.put(actor.userId(), idempotencyKey, requestHash, result.id(), resultTtl);
            } catch (DataAccessException cacheFailure) {
                log.warn("Transfer {} committed but its Redis idempotency cache could not be written", result.id());
            }
            return result;
        } finally {
            try {
                redis.release(actor.userId(), idempotencyKey, lockOwner);
            } catch (DataAccessException releaseFailure) {
                log.warn("Transfer idempotency lock will expire via TTL because release failed");
            }
        }
    }

    private TransferResponse cached(UUID actorId, String key, String requestHash) {
        return redis.get(actorId, key).map(cached -> {
            requireSameRequest(cached.requestHash(), requestHash);
            return transfer(cached.transferId());
        }).orElse(null);
    }

    private TransferResponse fromDatabase(UUID actorId, String key, String requestHash) {
        return idempotencyRecords.findByActorIdAndIdempotencyKey(actorId, key).map(record -> {
            requireSameRequest(record.getRequestHash(), requestHash);
            return transfer(record.getTransferId());
        }).orElse(null);
    }

    private TransferResponse transfer(UUID transferId) {
        Transfer transfer = transfers.findById(transferId).orElseThrow(() ->
                new IllegalStateException("Idempotency record references a missing transfer"));
        return TransferResponse.from(transfer);
    }

    private void requireSameRequest(String storedHash, String requestHash) {
        if (!MessageDigest.isEqual(storedHash.getBytes(StandardCharsets.US_ASCII),
                requestHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException("IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key was already used for a different transfer request", HttpStatus.CONFLICT);
        }
    }

    private String fingerprint(UUID source, UUID destination, BigDecimal amount, String description) {
        if (source == null || destination == null) {
            throw new BusinessException("ACCOUNT_NOT_FOUND", "Both account IDs are required", HttpStatus.NOT_FOUND);
        }
        String encodedDescription = Base64.getEncoder().encodeToString(
                (description == null ? "" : description).getBytes(StandardCharsets.UTF_8));
        String canonical = source + "\n" + destination + "\n" + amount.toPlainString() + "\n" + encodedDescription;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private BusinessException unavailable() {
        return new BusinessException("IDEMPOTENCY_SERVICE_UNAVAILABLE",
                "Transfer idempotency storage is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
