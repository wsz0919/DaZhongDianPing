package com.hmdp.dto;

import lombok.Data;

@Data
public class LoginFormEmailDTO {
    private String email;
    private String phone;
    private String code;
    private String password;
}
