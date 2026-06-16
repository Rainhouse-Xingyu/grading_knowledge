package com.neusoft.grading.dto;

import lombok.Data;

@Data
public class CasCallbackRequest {

    /** CAS 服务器返回的 ticket */
    private String ticket;

    /** 前端当前页面地址（可选，用于 CAS 重定向回跳） */
    private String service;
}
