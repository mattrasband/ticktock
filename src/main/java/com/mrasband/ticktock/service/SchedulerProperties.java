package com.mrasband.ticktock.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * @author matt.rasband
 */
@Component
@ConfigurationProperties(prefix = "ticktock.scheduler")
@Data
public class SchedulerProperties {
    private String exchange = "cron.schedule";
    private Map<String, String> cron = new HashMap<>();
}
