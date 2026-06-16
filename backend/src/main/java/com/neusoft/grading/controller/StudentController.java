package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "学生管理")
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    @Operation(summary = "查询所有学生")
    @GetMapping
    public Result<List<Student>> list() {
        return Result.ok(studentService.list());
    }

    @Operation(summary = "根据学号查询学生")
    @GetMapping("/{studentNo}")
    public Result<Student> getByNo(@PathVariable String studentNo) {
        return Result.ok(studentService.getById(studentNo));
    }

    @Operation(summary = "新增学生")
    @PostMapping
    public Result<Boolean> save(@RequestBody Student student) {
        return Result.ok(studentService.save(student));
    }

    @Operation(summary = "更新学生信息")
    @PutMapping
    public Result<Boolean> update(@RequestBody Student student) {
        return Result.ok(studentService.updateById(student));
    }

    @Operation(summary = "删除学生")
    @DeleteMapping("/{studentNo}")
    public Result<Boolean> delete(@PathVariable String studentNo) {
        return Result.ok(studentService.removeById(studentNo));
    }
}
