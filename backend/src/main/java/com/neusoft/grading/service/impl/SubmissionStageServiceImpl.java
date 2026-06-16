package com.neusoft.grading.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neusoft.grading.entity.SubmissionStage;
import com.neusoft.grading.mapper.SubmissionStageMapper;
import com.neusoft.grading.service.SubmissionStageService;
import org.springframework.stereotype.Service;

@Service
public class SubmissionStageServiceImpl extends ServiceImpl<SubmissionStageMapper, SubmissionStage> implements SubmissionStageService {
}
