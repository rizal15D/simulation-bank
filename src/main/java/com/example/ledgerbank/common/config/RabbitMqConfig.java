package com.example.ledgerbank.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    public static final String BANKING_EXCHANGE = "ledgerbank.events";
    public static final String NOTIFICATION_QUEUE = "ledgerbank.notification";
    public static final String AUDIT_QUEUE = "ledgerbank.audit";

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    ApplicationRunner declareRabbitTopology(RabbitAdmin rabbitAdmin) {
        return arguments -> rabbitAdmin.initialize();
    }

    @Bean
    Declarables bankingTopology() {
        TopicExchange exchange = new TopicExchange(BANKING_EXCHANGE, true, false);
        Queue notification = new Queue(NOTIFICATION_QUEUE, true);
        Queue audit = new Queue(AUDIT_QUEUE, true);
        Binding notificationBinding = BindingBuilder.bind(notification).to(exchange).with("transfer.*");
        Binding auditBinding = BindingBuilder.bind(audit).to(exchange).with("#");
        return new Declarables(exchange, notification, audit, notificationBinding, auditBinding);
    }
}
