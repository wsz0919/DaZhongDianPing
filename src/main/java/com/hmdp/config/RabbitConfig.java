package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;


/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/7/25 9:42
 * @Company:
 */
@Configuration
public class RabbitConfig {


    @Resource
    private ConnectionFactory connectionFactory;


    @Bean
    public RabbitAdmin rabbitAdmin() {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public Queue orderResultQueue() {
        return new Queue("order.result.queue", true);
    }

    @Bean
    public DirectExchange orderResultExchange() {
        return new DirectExchange("order.result.exchange", true, false);
    }

    @Bean
    public Binding orderResultBinding() {
        return BindingBuilder.bind(orderResultQueue())
                .to(orderResultExchange())
                .with("order.result.key");
    }

    public void directQueue(String queueName) {
        Queue queue = new Queue(queueName, true, false, false);
        rabbitAdmin().declareQueue(queue);
    }

    public void directExchange(String exchangeName) {
        DirectExchange directExchange = new DirectExchange(exchangeName, true, false);
        rabbitAdmin().declareExchange(directExchange);
    }

    public void bindingQueueExchange(String queueName, String exchangeName, String routingKey) {
        Binding bind = BindingBuilder.bind(new Queue(queueName)).to(new DirectExchange(exchangeName))
                .with(routingKey);
        rabbitAdmin().declareBinding(bind);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
