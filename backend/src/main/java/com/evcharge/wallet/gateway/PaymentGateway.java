package com.evcharge.wallet.gateway;

import com.evcharge.wallet.exception.PaymentException;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The payment gateway. Payments from many users are handled in parallel by a fixed pool
 * of worker THREADS (4), so a slow bank response never blocks other users' requests.
 * Every attempt is kept in a ConcurrentHashMap (COLLECTION safe for many threads).
 */
@Service
public class PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);
    private static final int TIMEOUT_SECONDS = 15;

    private final AtomicInteger workerNumber = new AtomicInteger(1);
    private final ExecutorService pool = Executors.newFixedThreadPool(4,
            task -> new Thread(task, "gateway-worker-" + workerNumber.getAndIncrement()));

    private final Map<String, GatewayTransaction> transactions = new ConcurrentHashMap<>();

    /**
     * Charges the payment method and waits for the result.
     * @throws com.evcharge.wallet.exception.PaymentDeclinedException if the provider says no
     * @throws PaymentException on timeout or interruption
     */
    public GatewayTransaction charge(PaymentMethod method, BigDecimal amount) {
        Future<GatewayTransaction> future = pool.submit(() -> process(method, amount));
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PaymentException("Payment gateway timed out, you have not been charged", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Payment was interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PaymentException pe) {
                throw pe;                                    // e.g. PaymentDeclinedException
            }
            throw new PaymentException("Unexpected gateway error", e.getCause());
        }
    }

    /** Runs on a worker thread. */
    private GatewayTransaction process(PaymentMethod method, BigDecimal amount) {
        String id = "GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String worker = Thread.currentThread().getName();
        try {
            method.authorize(amount);
            GatewayTransaction ok = new GatewayTransaction(id, method.name(), method.maskedInstrument(),
                    amount, "SUCCESS", Instant.now(), worker);
            transactions.put(id, ok);
            log.info("{} SUCCESS {} Rs {} on {}", id, method.maskedInstrument(), amount, worker);
            return ok;
        } catch (PaymentException e) {
            transactions.put(id, new GatewayTransaction(id, method.name(), method.maskedInstrument(),
                    amount, "FAILED", Instant.now(), worker));
            log.warn("{} FAILED {} Rs {} on {}: {}", id, method.maskedInstrument(), amount, worker, e.getMessage());
            throw e;
        }
    }

    /** Latest 20 gateway attempts, newest first (admin / demo screen). */
    public List<GatewayTransaction> recentTransactions() {
        return transactions.values().stream()
                .sorted(Comparator.comparing(GatewayTransaction::at).reversed())
                .limit(20)
                .toList();
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
    }
}
