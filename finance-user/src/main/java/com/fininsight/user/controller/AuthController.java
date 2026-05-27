package com.fininsight.user.controller;

import com.fininsight.common.result.R;
import com.fininsight.user.entity.User;
import com.fininsight.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        User user = userService.register(body.get("username"), body.get("password"));
        return R.ok(Map.of("userId", user.getId(), "message", "注册成功"));
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String token = userService.login(body.get("username"), body.get("password"));
        return R.ok(Map.of("token", token, "message", "登录成功"));
    }
}
