package com.hmdp.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ILock;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.jdbc.SQL;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.hmdp.utils.RabbitConstants.SECKILL_VOUCHER_EXCHANGE;
import static com.hmdp.utils.RabbitConstants.SECKILL_VOUCHER_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
@Slf4j
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 加载 判断秒杀券库存是否充足 并且 判断用户是否已下单 的Lua脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result getSeckillVoucher(Long id) {

        SeckillVoucher seckillVoucher = this.getById(id);

        // 判断秒杀时间是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始！");
        }

        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束！");
        }

        // 判断是否用户买过此优惠券
        Long userId = UserHolder.getUser().getId();
        RLock lock = redissonClient.getLock(LOCK_ORDER_KEY + userId);
        boolean result = lock.tryLock();
        if (!result) {
            return Result.fail("一人只能下一单！");
        }
        Long res;
        try {
            res = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    id.toString(),
                    userId.toString()
            );
        } catch (Exception e) {
            log.error("Lua脚本执行失败");
            throw new RuntimeException(e);
        }
        if (!res.equals(0L)) {
            // result为1表示库存不足，result为2表示用户已下单
            int r = res.intValue();
            return Result.fail(r == 2 ? "您已购买该优惠券！" : "库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(id);
        rabbitTemplate.convertAndSend(SECKILL_VOUCHER_EXCHANGE, SECKILL_VOUCHER_KEY, voucherOrder);
        return Result.ok(orderId);
    }

    @Transactional
    @Override
    public void createScekillOrder(VoucherOrder order) {
        Long id = order.getVoucherId();
        Long userId = order.getUserId();
        log.info("发送秒杀消息：userId={}, voucherId={}", userId, id);
        long count = voucherOrderService.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, id));
        if (count > 0) {
            log.error("您已购买该优惠券！");
            return ;
        }

        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SeckillVoucher::getVoucherId, id)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock - 1");
        boolean flag = this.update(wrapper);

        if (!flag){
            throw new RuntimeException("秒杀券扣减失败");
        }

        voucherOrderService.save(order);
    }
}
