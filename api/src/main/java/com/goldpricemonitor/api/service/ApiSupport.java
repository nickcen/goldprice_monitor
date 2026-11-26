package com.goldpricemonitor.api.service;
import com.goldpricemonitor.api.ApiFault;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;

public final class ApiSupport {
    private ApiSupport() {}
    public static ApiFault error(HttpStatus status,String code,String message) { return new ApiFault(status,code,message); }
    public static int page(Integer value) { if(value==null)return 1; if(value<1||value>1000000)throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","页码无效"); return value; }
    public static Map<String,Object> paged(List<Map<String,Object>> rows,long count,int page) { return Map.of("items",rows.stream().map(ApiSupport::publicRow).toList(),"page",page,"pageSize",10,"total",count,"totalPages",(count+9)/10); }
    public static Map<String,Object> publicRow(Map<String,Object> row) {
        Map<String,Object> out=new LinkedHashMap<>(); row.forEach((key,value)->{
            if(key.toLowerCase(Locale.ROOT).contains("password"))return;
            if(value instanceof java.sql.Timestamp t)out.put(key,t.toInstant().toString());
            else if(value instanceof java.sql.Date d)out.put(key,d.toLocalDate().toString());
            else if(value instanceof LocalDateTime d)out.put(key,d.toInstant(ZoneOffset.UTC).toString());
            else if(value instanceof LocalDate d)out.put(key,d.toString()); else out.put(key,value);
        }); return out;
    }
    public static void status(Integer value) { if(value==null||(value!=0&&value!=1))throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","状态只能为0或1"); }
}
