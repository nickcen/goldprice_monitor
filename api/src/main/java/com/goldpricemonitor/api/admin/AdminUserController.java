package com.goldpricemonitor.api.admin;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/users") public class AdminUserController {
    public record PasswordChange(String password){} public record StatusChange(Integer status){} public record ValidityChange(LocalDate validFromDate,LocalDate validToDate){}
    private final AuthService auth;private final UserService users;public AdminUserController(AuthService auth,UserService users){this.auth=auth;this.users=users;}
    @GetMapping public Map<String,Object> list(HttpServletRequest r,@RequestParam(required=false) String phone,@RequestParam(required=false) Integer status,@RequestParam(required=false) Integer page){auth.admin(r);return users.list(phone,status,page);}
    @GetMapping("/{id}") public Map<String,Object> get(HttpServletRequest r,@PathVariable int id){auth.admin(r);return users.get(id);}
    @PutMapping("/{id}/password") public Map<String,Object> password(HttpServletRequest r,@PathVariable int id,@RequestBody PasswordChange b){auth.admin(r);return users.password(id,b.password());}
    @PatchMapping("/{id}/status") public Map<String,Object> status(HttpServletRequest r,@PathVariable int id,@RequestBody StatusChange b){auth.admin(r);return users.status(id,b.status());}
    @PatchMapping("/{id}/validity") public Map<String,Object> validity(HttpServletRequest r,@PathVariable int id,@RequestBody ValidityChange b){auth.admin(r);return users.validity(id,b.validFromDate(),b.validToDate());}
}
