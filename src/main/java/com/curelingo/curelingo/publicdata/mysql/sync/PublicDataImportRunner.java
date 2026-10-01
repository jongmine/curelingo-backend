package com.curelingo.curelingo.publicdata.mysql.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mysql-sync")
public class PublicDataImportRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PublicDataImportRunner.class);

    private final EgenImportService importer;

    public PublicDataImportRunner(EgenImportService importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        long runId = importer.synchronize();
        log.info("Public hospital snapshot sync completed; runId={}", runId);
    }
}
