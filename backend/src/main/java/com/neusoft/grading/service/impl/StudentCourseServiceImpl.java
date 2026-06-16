package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neusoft.grading.entity.StudentCourse;
import com.neusoft.grading.mapper.StudentCourseMapper;
import com.neusoft.grading.service.StudentCourseService;
import org.springframework.stereotype.Service;

@Service
public class StudentCourseServiceImpl extends ServiceImpl<StudentCourseMapper, StudentCourse> implements StudentCourseService {
}
