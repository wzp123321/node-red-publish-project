package com.platform.controller;

import com.platform.common.Result;
import com.platform.entity.Token;
import com.platform.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - Token 管理
 */
@RestController
@RequestMapping("/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @GetMapping
    public Result<List<Token>> list() {
        return Result.ok(tokenService.list());
    }

    @PostMapping
    public Result<Token> create(@RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        return Result.ok(tokenService.create(remark));
    }

    @PostMapping("/{id}/revoke")
    public Result<Void> revoke(@PathVariable Long id) {
        tokenService.revoke(id);
        return Result.ok();
    }

    @PostMapping("/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        tokenService.enable(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tokenService.delete(id);
        return Result.ok();
    }
}