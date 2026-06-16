package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.entity.StudentCourse;
import com.neusoft.grading.service.StudentCourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "学生选课管理")
@RestController
@RequestMapping("/api/student-course")
@RequiredArgsConstructor
public class StudentCourseController {

    private final StudentCourseService studentCourseService;

    @Operation(summary = "查询所有选课记录")
    @GetMapping
    public Result<List<StudentCourse>> list() {
        return Result.ok(studentCourseService.list());
    }

    @Operation(summary = "查询某课程的选课学生")
    @GetMapping("/course/{courseId}")
    public Result<List<StudentCourse>> listByCourse(@PathVariable String courseId) {
        return Result.ok(studentCourseService.lambdaQuery()
                .eq(StudentCourse::getCourseId, courseId)
                .list());
    }

    @Operation(summary = "根据ID查询选课记录")
    @GetMapping("/{id}")
    public Result<StudentCourse> getById(@PathVariable Long id) {
        return Result.ok(studentCourseService.getById(id));
    }

    @Operation(summary = "新增选课记录")
    @PostMapping
    public Result<Boolean> save(@RequestBody StudentCourse record) {
        return Result.ok(studentCourseService.save(record));
    }

    @Operation(summary = "更新选课记录")
    @PutMapping
    public Result<Boolean> update(@RequestBody StudentCourse record) {
        return Result.ok(studentCourseService.updateById(record));
    }

    @Operation(summary = "删除选课记录")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(studentCourseService.removeById(id));
    }
}
