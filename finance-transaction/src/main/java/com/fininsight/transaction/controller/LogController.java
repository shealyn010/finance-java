package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    @GetMapping("/recent")
    public R<List<String>> recent(@RequestParam(defaultValue = "50") int lines) {
        try {
            Path logFile = Path.of("/tmp/finance-tx.log");
            if (!Files.exists(logFile)) return R.ok(List.of("日志文件不存在"));
            List<String> all = Files.readAllLines(logFile);
            int from = Math.max(0, all.size() - lines);
            return R.ok(all.subList(from, all.size()));
        } catch (IOException e) {
            return R.fail("读取日志失败: " + e.getMessage());
        }
    }

    @GetMapping("/errors")
    public R<List<String>> errors(@RequestParam(defaultValue = "20") int lines) {
        try {
            Path logFile = Path.of("/tmp/finance-tx.log");
            if (!Files.exists(logFile)) return R.ok(List.of("日志文件不存在"));
            List<String> result = new ArrayList<>();
            for (String line : Files.readAllLines(logFile)) {
                if (line.contains("ERROR") || line.contains("Exception") || line.contains("WARN")) {
                    result.add(line);
                }
            }
            int from = Math.max(0, result.size() - lines);
            return R.ok(result.subList(from, result.size()));
        } catch (IOException e) {
            return R.fail("读取日志失败: " + e.getMessage());
        }
    }
}
