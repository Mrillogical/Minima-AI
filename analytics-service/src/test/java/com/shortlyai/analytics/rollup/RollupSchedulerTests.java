package com.minima-AI.analytics.rollup;

import com.minima-AI.analytics.clicks.ClickEventRepository;
import com.minima-AI.analytics.clicks.ClickHourly;
import com.minima-AI.analytics.clicks.ClickHourlyRepository;
import com.minima-AI.analytics.clicks.TopUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RollupSchedulerTests {

    @Mock
    ClickEventRepository clickEventRepository;

    @Mock
    ClickHourlyRepository clickHourlyRepository;

    RollupScheduler rollupScheduler;

    @BeforeEach
    void setUp() {
        rollupScheduler = new RollupScheduler(clickEventRepository, clickHourlyRepository);
    }

    @Test
    void rollupPreviousHour_newUrlWithClicks_createsNewHourlyRow() {

        when(clickEventRepository.countGroupedByUrlIdBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new TopUrlResponse(100L, 25L)));

        when(clickHourlyRepository.findByUrlIdInAndHour(eq(List.of(100L)), any(Instant.class)))
                .thenReturn(List.of()); // no existing row

        rollupScheduler.rollupPreviousHour();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickHourly>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickHourlyRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getUrlId()).isEqualTo(100L);
        assertThat(captor.getValue().get(0).getClickCount()).isEqualTo(25L);
    }

    @Test
    void rollupPreviousHour_existingHourlyRow_updatesInPlaceRatherThanDuplicating() {

        ClickHourly existing = new ClickHourly(200L, Instant.now(), 10L);

        when(clickEventRepository.countGroupedByUrlIdBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(new TopUrlResponse(200L, 30L)));

        when(clickHourlyRepository.findByUrlIdInAndHour(eq(List.of(200L)), any(Instant.class)))
                .thenReturn(List.of(existing));

        rollupScheduler.rollupPreviousHour();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickHourly>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickHourlyRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).containsExactly(existing);
        assertThat(existing.getClickCount()).isEqualTo(30L); // overwritten, not added to
    }

    @Test
    void rollupPreviousHour_mixOfNewAndExistingUrls_batchesBothInOneSaveAll() {

        ClickHourly existing = new ClickHourly(1L, Instant.now(), 5L);

        when(clickEventRepository.countGroupedByUrlIdBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        new TopUrlResponse(1L, 15L),   // existing row, gets updated
                        new TopUrlResponse(2L, 40L)    // new row, gets created
                ));

        when(clickHourlyRepository.findByUrlIdInAndHour(eq(List.of(1L, 2L)), any(Instant.class)))
                .thenReturn(List.of(existing)); // only urlId=1 has a prior row

        rollupScheduler.rollupPreviousHour();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClickHourly>> captor = ArgumentCaptor.forClass(List.class);
        verify(clickHourlyRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(2);
        assertThat(existing.getClickCount()).isEqualTo(15L);
        assertThat(captor.getValue()).anySatisfy(row -> {
            assertThat(row.getUrlId()).isEqualTo(2L);
            assertThat(row.getClickCount()).isEqualTo(40L);
        });
    }

    @Test
    void rollupPreviousHour_noUrlsWithActivity_doesNothing() {

        when(clickEventRepository.countGroupedByUrlIdBetween(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        rollupScheduler.rollupPreviousHour();

        verify(clickHourlyRepository, never()).findByUrlIdInAndHour(any(), any());
        verify(clickHourlyRepository, never()).saveAll(any());
    }
}

