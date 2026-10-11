package com.nolla.bugnest.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    @GetMapping({"/", "/issues", "/settings", "/login", "/register"})
    public String index() {
        return "forward:/index.html";
    }
}
