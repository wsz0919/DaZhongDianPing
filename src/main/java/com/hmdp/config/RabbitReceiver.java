package com.hmdp.config;

import com.hmdp.dto.SeckillOrderDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
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
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RabbitTemplate rabbitTemplate;


    @RabbitListener(queues = "seckill.voucher.queue")
    public void receiveMessage(SeckillOrderDTO dto,
                                Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(dto.getUserId());
            voucherOrder.setVoucherId(dto.getVoucherId());
            log.info("发送秒杀消息：userId={}, voucherId={}", dto.getUserId(), dto.getVoucherId());
            boolean save = voucherOrderService.save(voucherOrder);

            if (!save){
                throw new RuntimeException("创建秒杀券订单失败");
            }

            // 返回确认信息
            channel.basicAck(tag, false);

            rabbitTemplate.convertAndSend("order.result.exchange", "order.result.key", orderId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
