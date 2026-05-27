package com.fininsight.transaction.controller;

import com.fininsight.common.result.R;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping("/jvm")
    public R<Map<String, Object>> jvm() {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("used_mb", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        m.put("total_mb", rt.totalMemory() / 1024 / 1024);
        m.put("max_mb", rt.maxMemory() / 1024 / 1024);
        m.put("threads", Thread.activeCount());
        m.put("cpus", rt.availableProcessors());
        return R.ok(m);
    }
}
