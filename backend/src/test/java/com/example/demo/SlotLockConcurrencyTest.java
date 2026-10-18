package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.repository.EvChargingSlotRepository;

/** Needs a running MySQL with the app's schema: EV_DB_TEST=true ./mvnw test -Dtest=SlotLockConcurrencyTest */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "EV_DB_TEST", matches = "true")
class SlotLockConcurrencyTest {

    @Autowired EvChargingSlotRepository slots;
    @Autowired TransactionTemplate tx;

    @Test
    void ofManySimultaneousLocksExactlyOneWins() throws Exception {
        long slotId = slots.findAll().stream().filter(s -> "AVAILABLE".equals(s.getStatus()))
                .findFirst().orElseThrow().getId();      // never touch a slot someone has booked

        int threads = 8;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long userId = i + 1;
            results.add(pool.submit(() -> {
                go.await();
                return tx.execute(s -> slots.tryLock(slotId, userId, LocalDateTime.now().plusMinutes(5)));
            }));
        }
        go.countDown();
        int winners = 0;
        for (Future<Integer> f : results) winners += f.get();
        pool.shutdown();

        assertEquals(1, winners);
        tx.executeWithoutResult(s -> slots.findById(slotId).ifPresent(sl -> {
            if ("SOFT_LOCKED".equals(sl.getStatus())) sl.setStatus("AVAILABLE");   // release our own hold only
        }));
    }
}
