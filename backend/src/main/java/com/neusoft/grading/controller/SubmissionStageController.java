package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.entity.SubmissionStage;
import com.neusoft.grading.service.SubmissionStageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "提交评审管理")
@RestController
@RequestMapping("/api/submission")
@RequiredArgsConstructor
public class SubmissionStageController {

    private final SubmissionStageService submissionStageService;

    @Operation(summary = "查询所有提交记录")
    @GetMapping
    public Result<List<SubmissionStage>> list() {
        return Result.ok(submissionStageService.list());
    }

    @Operation(summary = "查询学生某课程的提交记录")
    @GetMapping("/student/{studentNo}/course/{courseId}")
    public Result<List<SubmissionStage>> listByStudentAndCourse(
            @PathVariable String studentNo,
            @PathVariable String courseId) {
        return Result.ok(submissionStageService.lambdaQuery()
                .eq(SubmissionStage::getStudentNo, studentNo)
                .eq(SubmissionStage::getCourseId, courseId)
                .orderByAsc(SubmissionStage::getStageNum)
                .list());
    }

    @Operation(summary = "根据ID查询提交记录")
    @GetMapping("/{submissionId}")
    public Result<SubmissionStage> getById(@PathVariable Long submissionId) {
        return Result.ok(submissionStageService.getById(submissionId));
    }

    @Operation(summary = "新增提交记录")
    @PostMapping
    public Result<Boolean> save(@RequestBody SubmissionStage record) {
        return Result.ok(submissionStageService.save(record));
    }

    @Operation(summary = "更新提交记录")
    @PutMapping
    public Result<Boolean> update(@RequestBody SubmissionStage record) {
        return Result.ok(submissionStageService.updateById(record));
    }

    @Operation(summary = "删除提交记录")
    @DeleteMapping("/{submissionId}")
    public Result<Boolean> delete(@PathVariable Long submissionId) {
        return Result.ok(submissionStageService.removeById(submissionId));
    }
}
