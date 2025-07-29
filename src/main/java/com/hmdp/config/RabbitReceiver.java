package com.hmdp.config;

import com.hmdp.dto.SeckillOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/7/24 17:58
 * @Company:
 */
@Component
@Slf4j
public class RabbitReceiver {

    @Resource
    private ISeckillVoucherService seckillVoucherService;


    @RabbitListener(queues = "seckill.voucher.queue")
    public void receiveMessage(VoucherOrder voucherOrder,
                                Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            log.debug("线程: {} - \n收到优惠券订单消息：{}",Thread.currentThread().getName(), voucherOrder);
            seckillVoucherService.createScekillOrder(voucherOrder);
            // 返回确认信息
            channel.basicAck(tag, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
