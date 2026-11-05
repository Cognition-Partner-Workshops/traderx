package finos.traderx.accountservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DocsController {

    @GetMapping("/")
    public String index() {
        return "redirect:swagger-ui.html";
    }
}
