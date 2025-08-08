package com.hmdp.dto;

import lombok.Data;

/**
 * Description
 *
 * @Author wangshaozhe
 * @Date 2025/8/7 17:43
 * @Company:
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
