package com.goldpricemonitor.api.service;
import com.goldpricemonitor.api.mapper.UserMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.goldpricemonitor.api.service.ApiSupport.*;
@Service public class UserService {
    private final UserMapper users;private final AuthService auth;
    public UserService(UserMapper users,AuthService auth){this.users=users;this.auth=auth;}
    public Map<String,Object> list(String phone,Integer status,Integer page){if(status!=null)ApiSupport.status(status);int p=page(page);String filter=phone==null||phone.isBlank()?null:phone;return paged(users.list(filter,status,(p-1)*10),users.count(filter,status),p);}
    public Map<String,Object> get(int id){Map<String,Object> row=users.detail(id);if(row==null)throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","用户不存在");return publicRow(row);}
    private void exists(int id){if(users.byId(id)==null)throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","用户不存在");}
    @Transactional public Map<String,Object> password(int id,String value){exists(id);users.password(id,auth.password(value));auth.revokeUser(id);return Map.of("success",true);}
    public Map<String,Object> status(int id,Integer value){exists(id);ApiSupport.status(value);users.status(id,value);return Map.of("id",id,"status",value);}
    public Map<String,Object> validity(int id,LocalDate from,LocalDate to){exists(id);if(from!=null&&to!=null&&!to.isAfter(from))throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","结束日期须晚于开始日期");users.validity(id,from,to);Map<String,Object> out=new HashMap<>();out.put("id",id);out.put("validFromDate",from);out.put("validToDate",to);return out;}
}
