package com.goldpricemonitor.api.user;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/gold-price") public class GoldPriceController {
    private final AuthService auth;private final GoldPriceService prices;
    public GoldPriceController(AuthService auth,GoldPriceService prices){this.auth=auth;this.prices=prices;}
    @GetMapping("/latest") public Map<String,Object> latest(HttpServletRequest r){auth.user(r);return prices.latest();}
}
