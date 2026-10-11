package com.curelingo.curelingo.egen.batch;

import com.curelingo.curelingo.publicdata.mysql.sync.BedSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!mysql-sync & !bed-sync")
@ConditionalOnProperty(name = "curelingo.bed-refresh.enabled", havingValue = "true")
public class AvailableBedsBatchJob {
    private final BedSyncService beds;

    @Scheduled(fixedDelayString = "${curelingo.bed-refresh.interval:3m}",
               initialDelayString = "${curelingo.bed-refresh.initial-delay:3m}")
    public void fetchAndSaveAvailableBeds() {
        try {
            beds.synchronize();
        } catch (RuntimeException failure) {
            log.warn("응급 병상 수집 실패: 기존 데이터 유지, type={}", failure.getClass().getSimpleName());
        }
    }
}
