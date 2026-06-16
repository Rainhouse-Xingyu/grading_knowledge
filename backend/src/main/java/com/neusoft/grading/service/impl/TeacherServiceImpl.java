package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neusoft.grading.entity.Teacher;
import com.neusoft.grading.mapper.TeacherMapper;
import com.neusoft.grading.service.TeacherService;
import org.springframework.stereotype.Service;

@Service
public class TeacherServiceImpl extends ServiceImpl<TeacherMapper, Teacher> implements TeacherService {
}
