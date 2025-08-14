package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.MailConfig;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.LoginFormEmailDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;


/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MailConfig mailConfig;


    @Override
    public Result sendCodeByEmail(String email) {
        if (!RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式错误");
        }
        String code = RandomUtil.randomNumbers(6);

        if (RegexUtils.isEmailInvalid(email)) {
            try {
                mailConfig.sendSimpleMail(email, "大众点评登录验证码", "您好，您的验证码为：" + code + " （2分钟有效）");
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }

        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + email, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功，验证码：{}", code);
        //返回ok
        return Result.ok(code);
    }

    @Override
    public Result loginByEmail(LoginFormEmailDTO loginForm) {
        //校验手机号
        String email = loginForm.getEmail();
        String phone = loginForm.getPhone();
        if (!RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        if (!RegexUtils.isEmailInvalid(email)) {
            return Result.fail("邮箱格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + email);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //不一致 报错
            return Result.fail("验证码错误");
        }
        //一致 根据手机号查询用户
        User user = baseMapper
                .selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email).eq(User::getPhone, phone));
        //判断用户是否存在
        if (user == null) {
            //不存在 创建新用户
            user = createUserWithEmail(email, phone);
        }
        //生成token
        String token = UUID.randomUUID().toString(true);
        //userDTO转map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>()
                , CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (name, value) -> value.toString()
                        ));
        //保存用户信息到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }


    private User createUserWithEmail(String email, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setPhone(phone);
        //生成随机昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        baseMapper.insert(user);
        return user;
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (!RegexUtils.isPhoneInvalid(phone)) {
            //手机号不符合
            return Result.fail("手机号格式错误");
        }
        //手机号符合,生成验证码
        String code = RandomUtil.randomNumbers(6);
        /*//保存验证码到session
        session.setAttribute("code", code);*/
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.debug("发送验证码成功，验证码：{}", code);
        //返回ok
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (!RegexUtils.isPhoneInvalid(phone)) {
            //手机号不符合
            return Result.fail("手机号格式错误");
        }
        //从redis中获取验证码 校验验证码
        /*  Object cacheCode = session.getAttribute("code");*/
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            //不一致 报错
            return Result.fail("验证码错误");
        }
        //一致 根据手机号查询用户
        User user = baseMapper
                .selectOne(new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone));
        //判断用户是否存在
        if (user == null) {
            //不存在 创建新用户
            user = createUserWithPhone(phone);
        }
        /*//保存用户信息到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/
        //生成token
        String token = UUID.randomUUID().toString(true);
        //userDTO转map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>()
                , CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (name, value) -> value.toString()
                        ));
        //保存用户信息到redis
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, map);
        //设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //生成随机昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        baseMapper.insert(user);
        return user;
    }

    @Override
    public Result logOut(@RequestHeader("authorization") String token) {
        Boolean delete = stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        if (!delete) {
            return Result.fail("退出登录失败！");
        }
        return Result.ok("退出登录成功！");
    }

    @Override
    public Result sign() {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 记录连续签到的天数
     *
     * @return
     */
    @Override
    public Result signCount() {
        // 1、获取签到记录
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        // 拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        // 2、判断签到记录是否存在
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        // 3、获取本月的签到数（List<Long>是因为BitFieldSubCommands是一个子命令，可能存在多个返回结果，这里我们知识使用了Get，
        // 可以明确只有一个返回结果，即为本月的签到数，所以这里就可以直接通过get(0)来获取）
        Long num = result.get(0);
        if (num == null || num == 0) {
            // 二次判断签到结果是否存在，让代码更加健壮
            return Result.ok(0);
        }
        // 4、循环遍历，获取连续签到的天数（从当前天起始）
        int count = 0;
        while (true) {
            // 让这个数字与1做与运算，得到数字的最后一个bit位，并且判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            } else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

}
