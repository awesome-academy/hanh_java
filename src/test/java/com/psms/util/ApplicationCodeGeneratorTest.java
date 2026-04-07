package com.psms.util;

import com.psms.entity.Application;
import com.psms.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationCodeGeneratorTest {

    // Tên method mới sau QUALITY-6: dùng JPQL + Pageable thay vì derived query
    private static final String FIND_LATEST = "findLatestByApplicationCodePrefix";

    @Test
    void generatesFirstCodeWhenNoExistingCodeForDay() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        MutableClock clock = new MutableClock(LocalDate.of(2026, 3, 31));

        when(repository.findLatestByApplicationCodePrefix("HS-20260331-", Pageable.ofSize(1)))
                .thenReturn(List.of());

        ApplicationCodeGenerator generator = new ApplicationCodeGenerator(repository, clock);

        assertEquals("HS-20260331-00001", generator.generate());
    }

    @Test
    void resumesFromLatestCodeAndCachesSequenceWithinSameDay() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        MutableClock clock = new MutableClock(LocalDate.of(2026, 3, 31));

        when(repository.findLatestByApplicationCodePrefix("HS-20260331-", Pageable.ofSize(1)))
                .thenReturn(List.of(applicationWithCode("HS-20260331-00042")));

        ApplicationCodeGenerator generator = new ApplicationCodeGenerator(repository, clock);

        assertEquals("HS-20260331-00043", generator.generate());
        assertEquals("HS-20260331-00044", generator.generate());
        // Verify DB chỉ được gọi 1 lần (cache hoạt động đúng)
        verify(repository, times(1))
                .findLatestByApplicationCodePrefix("HS-20260331-", Pageable.ofSize(1));
    }

    @Test
    void resetsSequenceWhenDateChanges() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        MutableClock clock = new MutableClock(LocalDate.of(2026, 3, 31));

        when(repository.findLatestByApplicationCodePrefix("HS-20260331-", Pageable.ofSize(1)))
                .thenReturn(List.of(applicationWithCode("HS-20260331-00007")));
        when(repository.findLatestByApplicationCodePrefix("HS-20260401-", Pageable.ofSize(1)))
                .thenReturn(List.of());

        ApplicationCodeGenerator generator = new ApplicationCodeGenerator(repository, clock);

        assertEquals("HS-20260331-00008", generator.generate());
        clock.setDate(LocalDate.of(2026, 4, 1));
        assertEquals("HS-20260401-00001", generator.generate());
    }

    @Test
    void generatesUniqueCodesConcurrently() throws ExecutionException, InterruptedException {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        MutableClock clock = new MutableClock(LocalDate.of(2026, 3, 31));

        when(repository.findLatestByApplicationCodePrefix(anyString(), eq(Pageable.ofSize(1))))
                .thenReturn(List.of());

        ApplicationCodeGenerator generator = new ApplicationCodeGenerator(repository, clock);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                tasks.add(generator::generate);
            }

            List<Future<String>> futures = executor.invokeAll(tasks);
            Set<String> generatedCodes = new TreeSet<>();
            for (Future<String> future : futures) {
                generatedCodes.add(future.get());
            }

            assertEquals(100, generatedCodes.size());
            assertTrue(generatedCodes.contains("HS-20260331-00001"));
            assertTrue(generatedCodes.contains("HS-20260331-00100"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Application applicationWithCode(String applicationCode) {
        Application application = new Application();
        application.setApplicationCode(applicationCode);
        return application;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneId.systemDefault();

        private MutableClock(LocalDate date) {
            this.instant = date.atStartOfDay(zone).toInstant();
        }

        void setDate(LocalDate date) {
            this.instant = date.atStartOfDay(zone).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
