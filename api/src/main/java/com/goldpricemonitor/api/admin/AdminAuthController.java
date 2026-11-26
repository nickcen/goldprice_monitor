package com.goldpricemonitor.api.admin;
import com.goldpricemonitor.api.service.AuthService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/auth") public class AdminAuthController {
    public record Login(String username,String password){}
    private final AuthService auth;public AdminAuthController(AuthService auth){this.auth=auth;}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Login body){return auth.adminLogin(body.username(),body.password());}
}
