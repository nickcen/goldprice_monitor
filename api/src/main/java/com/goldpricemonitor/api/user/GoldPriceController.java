package com.goldpricemonitor.api.user;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.goldpricemonitor.api.service.ApiSupport.error;
@RestController @RequestMapping("/api/v1/gold-price") public class GoldPriceController {
    private static final Logger log = LoggerFactory.getLogger(GoldPriceController.class);
    private final AuthService auth;private final GoldPriceService prices;
    public GoldPriceController(AuthService auth,GoldPriceService prices){this.auth=auth;this.prices=prices;}
    @GetMapping("/latest") public Map<String,Object> latest(HttpServletRequest r){
        int userId = auth.user(r).id();
        Map<String,Object> result = prices.latest();
        log.info("Gold price request endpoint=latest userId={} price={} dataTime={}", userId, result.get("price"), result.get("dataTime"));
        return result;
    }
    @GetMapping("/day") public List<Map<String,Object>> day(HttpServletRequest r, @RequestParam LocalDate date){
        int userId = auth.user(r).id();
        List<Map<String,Object>> result = prices.day(date);
        double min = result.stream().mapToDouble(row -> ((Number) row.get("price")).doubleValue()).min().orElse(Double.NaN);
        double max = result.stream().mapToDouble(row -> ((Number) row.get("price")).doubleValue()).max().orElse(Double.NaN);
        log.info("Gold price request endpoint=day userId={} date={} count={} minPrice={} maxPrice={}", userId, date, result.size(), min, max);
        return result;
    }
    @GetMapping("/at") public Map<String,Object> at(HttpServletRequest r, @RequestParam String time){
        int userId = auth.user(r).id();
        try {
            Map<String,Object> result = prices.at(java.time.OffsetDateTime.parse(time).toInstant());
            log.info("Gold price request endpoint=at userId={} time={} price={} dataTime={}", userId, time, result.get("price"), result.get("dataTime"));
            return result;
        }
        catch (java.time.format.DateTimeParseException e) { throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","时间须为含时区的 ISO 8601 格式"); }
    }
}
