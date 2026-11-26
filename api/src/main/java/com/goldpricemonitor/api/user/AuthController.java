package com.goldpricemonitor.api.user;
import com.goldpricemonitor.api.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth") public class AuthController {
    public record Login(String phone,String password){}
    private final AuthService auth;public AuthController(AuthService auth){this.auth=auth;}
    @PostMapping("/register") public Map<String,Object> register(@RequestBody Login body){return auth.register(body.phone(),body.password());}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Login body){return auth.login(body.phone(),body.password());}
    @GetMapping("/session") public Map<String,Object> session(HttpServletRequest r){return auth.session(r);}
    @PostMapping("/refresh") public Map<String,Object> refresh(HttpServletRequest r){return auth.refresh(r);}
    @PostMapping("/logout") public Map<String,Object> logout(HttpServletRequest r){return auth.logout(r);}
}
