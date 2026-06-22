package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.BatchImportResult;
import com.neusoft.grading.entity.Teacher;
import com.neusoft.grading.service.LocalAuthService;
import com.neusoft.grading.service.TeacherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "教师管理")
@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;
    private final LocalAuthService localAuthService;

    @Operation(summary = "查询所有教师")
    @GetMapping
    public Result<List<Teacher>> list() {
        return Result.ok(teacherService.list());
    }

    @Operation(summary = "根据工号查询教师")
    @GetMapping("/{teacherNo}")
    public Result<Teacher> getByNo(@PathVariable String teacherNo) {
        return Result.ok(teacherService.getById(teacherNo));
    }

    @Operation(summary = "新增教师")
    @PostMapping
    public Result<Boolean> save(@RequestBody Teacher teacher) {
        return Result.ok(teacherService.save(teacher));
    }

    @Operation(summary = "更新教师信息")
    @PutMapping
    public Result<Boolean> update(@RequestBody Teacher teacher) {
        return Result.ok(teacherService.updateById(teacher));
    }

    @Operation(summary = "删除教师")
    @DeleteMapping("/{teacherNo}")
    public Result<Boolean> delete(@PathVariable String teacherNo) {
        return Result.ok(teacherService.removeById(teacherNo));
    }
}
