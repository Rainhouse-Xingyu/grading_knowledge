package com.neusoft.grading.controller;

import com.neusoft.grading.common.Result;
import com.neusoft.grading.dto.BatchImportResult;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.service.LocalAuthService;
import com.neusoft.grading.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "学生管理")
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final LocalAuthService localAuthService;

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

    // ==================== 批量导入 ====================

    @Operation(summary = "批量导入学生（Excel）并创建本地登录账号")
    @PostMapping("/batch-import")
    public Result<BatchImportResult> batchImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "defaultPassword", required = false, defaultValue = "Neusoft@2026") String defaultPassword,
            @RequestParam(value = "courseId", required = false) String courseId) {

        if (file.isEmpty()) {
            return Result.fail(400, "上传文件为空");
        }

        // 校验文件格式
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
            return Result.fail(400, "仅支持 .xlsx 或 .xls 格式的 Excel 文件");
        }

        BatchImportResult result = localAuthService.batchImportStudents(file, defaultPassword, courseId);
        return Result.ok(result);
    }
}
