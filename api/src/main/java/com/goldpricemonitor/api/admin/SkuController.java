package com.goldpricemonitor.api.admin;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/skus") public class SkuController {
    private final AuthService auth;private final SkuService skus;public SkuController(AuthService auth,SkuService skus){this.auth=auth;this.skus=skus;}
    @GetMapping public Map<String,Object> list(HttpServletRequest r,@RequestParam(required=false) String name,@RequestParam(required=false) Integer status,@RequestParam(required=false) Integer page){auth.admin(r);return skus.list(name,status,page);}
    @GetMapping("/{id}") public Map<String,Object> get(HttpServletRequest r,@PathVariable int id){auth.admin(r);return skus.get(id);}
    @PostMapping public ResponseEntity<Map<String,Object>> create(HttpServletRequest r,@RequestBody SkuService.Input b){auth.admin(r);return ResponseEntity.status(201).body(skus.create(b));}
    @PutMapping("/{id}") public Map<String,Object> update(HttpServletRequest r,@PathVariable int id,@RequestBody SkuService.Input b){auth.admin(r);return skus.update(id,b);}
    @DeleteMapping("/{id}") public Map<String,Object> delete(HttpServletRequest r,@PathVariable int id){auth.admin(r);return skus.delete(id);}
}
