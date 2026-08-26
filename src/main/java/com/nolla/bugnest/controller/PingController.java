package com.nolla.bugnest.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {
    @GetMapping("/api/ping")
    public Map<String, String> ping(){
        return Map.of(
            "data", "pong",
            "message", "ok"
        );
    }

    @GetMapping("/api/add")
    public int add(@RequestParam int a, @RequestParam(required = false, defaultValue = "1") int b){ return a+b; }
}
