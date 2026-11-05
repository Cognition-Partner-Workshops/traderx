package finos.traderx.tradeservice.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocsController {

    @GetMapping("/")
    public String index() {
        return "redirect:swagger-ui.html";
    }
}
