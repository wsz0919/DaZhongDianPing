package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormEmailDTO;
import com.hmdp.dto.LoginFormPhoneDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     *
     * @param email   邮箱
     * @return {@link Result}
     */
    Result sendCodeByEmail(String email);

    /**
     * 登录
     *
     * @param loginForm 登录表单
     * @return {@link Result}
     */
    Result loginByEmail(LoginFormEmailDTO loginForm);
}
