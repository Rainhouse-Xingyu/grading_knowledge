package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neusoft.grading.entity.CourseProject;
import com.neusoft.grading.mapper.CourseProjectMapper;
import com.neusoft.grading.service.CourseProjectService;
import org.springframework.stereotype.Service;

@Service
public class CourseProjectServiceImpl extends ServiceImpl<CourseProjectMapper, CourseProject> implements CourseProjectService {
}
