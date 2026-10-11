package com.curelingo.curelingo.publicdata.mysql.sync;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bed-sync")
public class BedSyncRunner implements ApplicationRunner {
    private final BedSyncService beds;
    private final ConfigurableApplicationContext context;

    public BedSyncRunner(BedSyncService beds, ConfigurableApplicationContext context) {
        this.beds = beds;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        beds.synchronize();
        context.close();
    }
}
