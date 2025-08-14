package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.*;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // 实现登录功能
        return userService.login(loginForm,session);
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/codeByEmail")
    public Result sendCodeByEmail(@RequestParam("email") String email) {
        // 发送短信验证码并保存验证码
        return userService.sendCodeByEmail(email);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含邮箱、验证码；或者邮箱、密码
     */
    @PostMapping("/loginByEmail")
    public Result loginByEmail(@RequestBody LoginFormEmailDTO loginForm){
        // 实现登录功能
        return userService.loginByEmail(loginForm);
    }

    /**
     * 登出功能
     *
     * @param token
     */
    @PostMapping("/logout")
    public Result logout(@RequestHeader("authorization") String token){
        return userService.logOut(token);
    }

    @GetMapping("/me")
    public Result me(){
        //  获取当前登录的用户并返回
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 查询用户id
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 用户签到
     * @return
     */
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    /**
     * 记录连续签到的天数
     * @return
     */
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    /**
     * 更新用户信息
     * @return
     */
    @PostMapping("/info")
    public Result updateInfo(@RequestBody String name) {
        return Result.ok(1);
    }

}
