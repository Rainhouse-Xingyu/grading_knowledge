package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.entity.CourseProject;
import com.neusoft.grading.service.CourseProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "课程项目管理")
@RestController
@RequestMapping("/api/course")
@RequiredArgsConstructor
public class CourseProjectController {

    private final CourseProjectService courseProjectService;

    @Operation(summary = "查询所有课程")
    @GetMapping
    public Result<List<CourseProject>> list() {
        return Result.ok(courseProjectService.list());
    }

    @Operation(summary = "根据ID查询课程")
    @GetMapping("/{courseId}")
    public Result<CourseProject> getById(@PathVariable String courseId) {
        return Result.ok(courseProjectService.getById(courseId));
    }

    @Operation(summary = "新增课程")
    @PostMapping
    public Result<Boolean> save(@RequestBody CourseProject course) {
        return Result.ok(courseProjectService.save(course));
    }

    @Operation(summary = "更新课程信息")
    @PutMapping
    public Result<Boolean> update(@RequestBody CourseProject course) {
        return Result.ok(courseProjectService.updateById(course));
    }

    @Operation(summary = "删除课程")
    @DeleteMapping("/{courseId}")
    public Result<Boolean> delete(@PathVariable String courseId) {
        return Result.ok(courseProjectService.removeById(courseId));
    }
}
