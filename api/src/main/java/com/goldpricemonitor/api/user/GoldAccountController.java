package com.goldpricemonitor.api.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.goldpricemonitor.api.service.AuthService;
import com.goldpricemonitor.api.service.GoldAccountService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/gold-accounts")
public class GoldAccountController {
    private final AuthService auth;
    private final GoldAccountService accounts;
    public GoldAccountController(AuthService auth, GoldAccountService accounts) { this.auth=auth; this.accounts=accounts; }
    public record AccountInput(String username,String password) {}
    public record LoginInput(String mobile,String code) {}
    public record PasswordLoginInput(String code,String cachkey) {}
    public record SellInput(java.math.BigDecimal weight) {}
    @GetMapping public List<Map<String,Object>> list(HttpServletRequest request) { return accounts.list(auth.user(request).id()); }
    @GetMapping("/grabcoin") public Map<String,Object> grabcoin(HttpServletRequest request) { return accounts.grabcoin(auth.user(request).id()); }
    @GetMapping("/{id}") public Map<String,Object> get(HttpServletRequest request,@PathVariable int id) { return accounts.getForEdit(auth.user(request).id(),id); }
    @GetMapping("/{id}/sellable") public Map<String,Object> sellable(HttpServletRequest request,@PathVariable int id) { return accounts.sellable(auth.user(request).id(),id); }
    @PostMapping("/{id}/grabcoin/sell") public Map<String,Object> sellGrabcoin(HttpServletRequest request,@PathVariable int id,@RequestBody SellInput body) { return accounts.sellGrabcoin(auth.user(request).id(),id,body.weight()); }
    @PostMapping public Map<String,Object> create(HttpServletRequest request,@RequestBody AccountInput body) { return accounts.create(auth.user(request).id(),body.username(),body.password()); }
    @PatchMapping("/{id}") public Map<String,Object> update(HttpServletRequest request,@PathVariable int id,@RequestBody JsonNode body) {
        return accounts.update(auth.user(request).id(),id,body.path("username").asText(null),body.path("password").asText(null),body.has("password"));
    }
    @DeleteMapping("/{id}") public Map<String,Object> delete(HttpServletRequest request,@PathVariable int id) { accounts.delete(auth.user(request).id(),id); return Map.of("success",true); }
    @PostMapping("/{id}/send-sms") public Map<String,Object> sendSms(HttpServletRequest request,@PathVariable int id,@RequestBody LoginInput body) { return accounts.sendSms(auth.user(request).id(),id,body.mobile()); }
    @PostMapping("/{id}/login") public Map<String,Object> login(HttpServletRequest request,@PathVariable int id,@RequestBody LoginInput body) { return accounts.login(auth.user(request).id(),id,body.mobile(),body.code()); }
    @PostMapping("/{id}/logout") public Map<String,Object> logout(HttpServletRequest request,@PathVariable int id) { return accounts.logout(auth.user(request).id(),id); }
    @GetMapping("/{id}/password-code") public Map<String,Object> passwordCode(HttpServletRequest request,@PathVariable int id) { return accounts.passwordCode(auth.user(request).id(),id); }
    @PostMapping("/{id}/password-login") public Map<String,Object> passwordLogin(HttpServletRequest request,@PathVariable int id,@RequestBody PasswordLoginInput body) { return accounts.passwordLogin(auth.user(request).id(),id,body.code(),body.cachkey()); }
}
