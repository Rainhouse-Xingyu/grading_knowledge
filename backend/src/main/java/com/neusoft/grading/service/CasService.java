package com.neusoft.grading.service;

import com.neusoft.grading.entity.Student;
import com.neusoft.grading.entity.Teacher;

/**
 * CAS 统一身份认证服务
 */
public interface CasService {

    /**
     * 向 CAS 服务器验证 ticket 有效性，并返回学生信息（若为学生账号）
     *
     * @param ticket  CAS 返回的 ticket
     * @param service 本系统服务地址
     * @return 学生信息，若非学生账号返回 null
     */
    Student validateAndExtractStudent(String ticket, String service);

    /**
     * 向 CAS 服务器验证 ticket 有效性，并返回教师信息（若为教师账号）
     *
     * @param ticket  CAS 返回的 ticket
     * @param service 本系统服务地址
     * @return 教师信息，若非教师账号返回 null
     */
    Teacher validateAndExtractTeacher(String ticket, String service);

    /**
     * 获取 CAS 登出重定向 URL
     */
    String getCasLogoutUrl();
}
