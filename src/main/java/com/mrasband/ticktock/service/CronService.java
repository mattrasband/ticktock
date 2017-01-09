package com.mrasband.ticktock.service;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;

/**
 * @author matt.rasband
 */
@Service
@Slf4j
public class CronService {
    private final AmqpAdmin amqpAdmin;
    private final AmqpTemplate amqpTemplate;
    private final TaskScheduler scheduler;
    private final SchedulerProperties tickTockProperties;

    @Autowired
    public CronService(AmqpAdmin amqpAdmin,
                       AmqpTemplate amqpTemplate,
                       TaskScheduler scheduler,
                       SchedulerProperties tickTockProperties) {
        this.amqpAdmin = amqpAdmin;
        this.amqpTemplate = amqpTemplate;
        this.scheduler = scheduler;
        this.tickTockProperties = tickTockProperties;
    }

    @PostConstruct
    void init() {
        amqpAdmin.declareExchange(new TopicExchange(tickTockProperties.getExchange()));
        createSemaphoreQueue();
    }

    @RabbitListener(queues = "scheduler.semaphore")
    void receivedSemaphore(Object body, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        log.info("RECEIVED SEMAPHORE!");
        createCronJobs();
    }

    private void createCronJobs() {
        tickTockProperties.getCron()
                .forEach((cron, routingKey) -> {
                    CronEventPublisher publisher = new CronEventPublisher(amqpTemplate, tickTockProperties.getExchange(), routingKey);
                    scheduler.schedule(publisher, new CronTrigger(cron, TimeZone.getTimeZone("UTC")));
                });
    }

    private void createSemaphoreQueue() {
        String semaphoreQueue = amqpAdmin.declareQueue(new Queue("scheduler.semaphore", true,
                false, false, ImmutableMap.of("x-max-length", 1)));
        Properties props = amqpAdmin.getQueueProperties(semaphoreQueue);
        Integer messageCount = (Integer) props.get("QUEUE_MESSAGE_COUNT");
        Integer queueConsumerCount = (Integer) props.get("QUEUE_CONSUMER_COUNT");
        if (messageCount < 1 && queueConsumerCount == 0) { // otherwise assume the another one probably has the semaphore
            amqpTemplate.send("", semaphoreQueue, MessageBuilder
                    .withBody("{}".getBytes())
                    .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build());
        }
    }

    @Slf4j
    private static class CronEventPublisher implements Runnable {
        private final AmqpTemplate amqpTemplate;
        private final String exchange;
        private final String eventName;

        CronEventPublisher(AmqpTemplate amqpTemplate, String exchange, String eventName) {
            this.amqpTemplate = amqpTemplate;
            this.exchange = exchange;
            this.eventName = eventName;
        }

        @Override
        public void run() {
            log.info("Publishing cron event \"{}\"", this.eventName);

            ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));

            Message message = MessageBuilder
                    .withBody("{}".getBytes())  // body can't be empty :shrug:
                    .setCorrelationIdString(UUID.randomUUID().toString())
                    .setContentType(MediaType.APPLICATION_JSON_VALUE)
                    .setTimestamp(Date.from(timestamp.toInstant()))
                    .setHeader("event", this.eventName)
                    .build();

            amqpTemplate.send(this.exchange, this.eventName, message);
        }
    }
}
