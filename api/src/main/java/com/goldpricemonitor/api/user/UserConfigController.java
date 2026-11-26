package com.goldpricemonitor.api.user;
import com.fasterxml.jackson.databind.JsonNode;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/me/config") public class UserConfigController {
    private final AuthService auth;private final UserConfigService configs;
    public UserConfigController(AuthService auth,UserConfigService configs){this.auth=auth;this.configs=configs;}
    @GetMapping public Map<String,Object> get(HttpServletRequest r){return configs.get(auth.user(r).id());}
    @PatchMapping public Map<String,Object> update(HttpServletRequest r,@RequestBody JsonNode body){return configs.update(auth.user(r).id(),body);}
}
