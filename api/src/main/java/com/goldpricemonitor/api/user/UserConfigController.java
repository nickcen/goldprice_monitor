package com.goldpricemonitor.api.user;
import com.fasterxml.jackson.databind.JsonNode;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/me/config") public class UserConfigController {
    private static final Logger log = LoggerFactory.getLogger(UserConfigController.class);
    private final AuthService auth;private final UserConfigService configs;
    public UserConfigController(AuthService auth,UserConfigService configs){this.auth=auth;this.configs=configs;}
    @GetMapping public Map<String,Object> get(HttpServletRequest r){return configs.get(auth.user(r).id());}
    @PatchMapping public Map<String,Object> update(HttpServletRequest r,@RequestBody JsonNode body){
        int userId=auth.user(r).id();
        log.info("Threshold update received userId={}",userId);
        try {
            Map<String,Object> saved=configs.update(userId,body);
            log.info("Threshold update saved userId={} minPrice={} maxPrice={}",userId,saved.get("minPrice"),saved.get("maxPrice"));
            return saved;
        } catch (RuntimeException e) {
            log.warn("Threshold update failed userId={} error={}",userId,e.getClass().getSimpleName());
            throw e;
        }
    }
}
