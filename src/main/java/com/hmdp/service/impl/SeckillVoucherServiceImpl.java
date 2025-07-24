package com.hmdp.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.jdbc.SQL;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;

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
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private RedisIdWorker redisIdWorker;

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
        // 判断是否还有库存
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("库存不足！");
        }

        // 判断是否用户买过此优惠券
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            ISeckillVoucherService proxy = (ISeckillVoucherService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId, id);
        }
    }

    @Transactional
    @Override
    public Result createVoucherOrder(Long userId, Long id) {
        long count = voucherOrderService.count(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, id));
        if (count > 0) {
            return Result.fail("您已购买该优惠券！");
        }

        LambdaUpdateWrapper<SeckillVoucher> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SeckillVoucher::getVoucherId, id)
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock = stock - 1");
        boolean flag = this.update(wrapper);

        if (!flag){
            throw new RuntimeException("秒杀券扣减失败");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(id);
        flag = voucherOrderService.save(voucherOrder);
        if (!flag){
            throw new RuntimeException("创建秒杀券订单失败");
        }
        return Result.ok(orderId);
    }
}
