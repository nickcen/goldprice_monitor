package com.goldpricemonitor.api.user;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.goldpricemonitor.api.service.ApiSupport.error;
@RestController @RequestMapping("/api/v1/gold-price") public class GoldPriceController {
    private final AuthService auth;private final GoldPriceService prices;
    public GoldPriceController(AuthService auth,GoldPriceService prices){this.auth=auth;this.prices=prices;}
    @GetMapping("/latest") public Map<String,Object> latest(HttpServletRequest r){auth.user(r);return prices.latest();}
    @GetMapping("/at") public Map<String,Object> at(HttpServletRequest r, @RequestParam String time){
        auth.user(r);
        try { return prices.at(java.time.OffsetDateTime.parse(time).toInstant()); }
        catch (java.time.format.DateTimeParseException e) { throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","时间须为含时区的 ISO 8601 格式"); }
    }
}
