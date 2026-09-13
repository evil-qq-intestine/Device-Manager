package com.example.tool.webController;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping
public class WebController {

    @GetMapping("/index")
    public ModelAndView index() {
        return new ModelAndView("redirect:/index.html");
    }

    @GetMapping("/login")
    public ModelAndView login() {
        return new ModelAndView("redirect:/index.html");
    }

}