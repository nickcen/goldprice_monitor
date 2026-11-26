package com.goldpricemonitor.api.admin;
import com.goldpricemonitor.api.service.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/payments") public class PaymentController {
    private final AuthService auth;private final PaymentService payments;public PaymentController(AuthService auth,PaymentService payments){this.auth=auth;this.payments=payments;}
    @GetMapping public Map<String,Object> list(HttpServletRequest r,@RequestParam(required=false) Integer userId,@RequestParam(required=false) String paymentNumber,@RequestParam(required=false) Instant createdFrom,@RequestParam(required=false) Instant createdTo,@RequestParam(required=false) Instant paidFrom,@RequestParam(required=false) Instant paidTo,@RequestParam(required=false) Integer page){auth.admin(r);return payments.list(userId,paymentNumber,createdFrom,createdTo,paidFrom,paidTo,page);}
}
