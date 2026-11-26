package com.goldpricemonitor.api.service;
import com.goldpricemonitor.api.mapper.PaymentMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.goldpricemonitor.api.service.ApiSupport.*;
@Service public class PaymentService {
    private final PaymentMapper mapper;public PaymentService(PaymentMapper mapper){this.mapper=mapper;}
    private LocalDateTime utc(Instant i){return i==null?null:LocalDateTime.ofInstant(i,ZoneOffset.UTC);}
    public Map<String,Object> list(Integer userId,String paymentNumber,Instant createdFrom,Instant createdTo,Instant paidFrom,Instant paidTo,Integer page){if(createdFrom!=null&&createdTo!=null&&!createdTo.isAfter(createdFrom)||paidFrom!=null&&paidTo!=null&&!paidTo.isAfter(paidFrom))throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","时间范围无效");int p=page(page);String number=paymentNumber==null||paymentNumber.isBlank()?null:paymentNumber;LocalDateTime cf=utc(createdFrom),ct=utc(createdTo),pf=utc(paidFrom),pt=utc(paidTo);return paged(mapper.list(userId,number,cf,ct,pf,pt,(p-1)*10),mapper.count(userId,number,cf,ct,pf,pt),p);}
}
