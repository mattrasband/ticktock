package com.mrasband.ticktock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * @author matt.rasband
 */
@Service
public class CronService {
    private static final Logger LOG = LoggerFactory.getLogger(CronService.class);

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final TaskScheduler scheduler;
    private final TickTockProperties tickTockProperties;

    @Autowired
    public CronService(RabbitAdmin rabbitAdmin,
                       RabbitTemplate rabbitTemplate,
                       TaskScheduler scheduler,
                       TickTockProperties tickTockProperties) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.scheduler = scheduler;
        this.tickTockProperties = tickTockProperties;
    }

    @PostConstruct
    void createCronScheduler() {
        rabbitAdmin.declareExchange(new TopicExchange(tickTockProperties.getExchange()));

        tickTockProperties.getCron()
                .forEach((cron, routingKey) -> scheduler.schedule(publishCronEvent(routingKey),
                        new CronTrigger(cron, TimeZone.getTimeZone("UTC"))));
    }

    private Runnable publishCronEvent(String routingKey) {
        return () -> {
            LOG.info("Publishing cron event \"{}\"", routingKey);

            ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));

            Message message = MessageBuilder
                    .withBody("{}".getBytes())  // body can't be empty :shrug:
                    .setCorrelationIdString(UUID.randomUUID().toString())
                    .setContentType(MediaType.APPLICATION_JSON_VALUE)
                    .setTimestamp(Date.from(timestamp.toInstant()))
                    .setHeader("event", routingKey)
                    .build();

            rabbitTemplate.send(tickTockProperties.getExchange(), routingKey, message);
        };
    }

    @Component
    @ConfigurationProperties(prefix = "ticktock.scheduler")
    public static class TickTockProperties {
        private String exchange = "cron.schedule";
        private Map<String, String> cron = new HashMap<>();

        public Map<String, String> getCron() {
            return cron;
        }

        public void setCron(Map<String, String> cron) {
            this.cron = cron;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }
    }
}
