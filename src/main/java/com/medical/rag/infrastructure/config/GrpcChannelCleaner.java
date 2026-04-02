package com.medical.rag.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class GrpcChannelCleaner {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelCleaner.class);

    @Scheduled(fixedRate = 60000)
    public void cleanOrphanedChannels() {
        long before = Runtime.getRuntime().freeMemory();
        System.gc();
        long after = Runtime.getRuntime().freeMemory();
        long freedMB = (after - before) / (1024 * 1024);
        if (freedMB > 10) {
            log.info("GC liberou ~{}MB de memória (canais gRPC órfãos)", freedMB);
        }
    }
}
