package com.goldpricemonitor.api.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.goldpricemonitor.api.mapper.UserConfigMapper;
import com.goldpricemonitor.api.entity.UserConfig;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.goldpricemonitor.api.service.ApiSupport.error;
@Service public class UserConfigService {
    private final UserConfigMapper mapper; private final String unit,currency;
    public UserConfigService(UserConfigMapper mapper,@Value("${app.unit:gram}") String unit,@Value("${app.currency:CNY}") String currency){this.mapper=mapper;this.unit=unit;this.currency=currency;}
    public Map<String,Object> get(int id){UserConfig c=mapper.byUser(id);if(c==null)throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","配置不存在");Map<String,Object> out=new LinkedHashMap<>();out.put("level",c.getLevel());out.put("monitorIntervalSeconds",c.getMonitorIntervalSeconds());out.put("minPrice",c.getMinPrice()==null?null:c.getMinPrice().toPlainString());out.put("maxPrice",c.getMaxPrice()==null?null:c.getMaxPrice().toPlainString());out.put("unit",unit);out.put("currency",currency);out.put("precision",2);return out;}
    public BigDecimal decimal(JsonNode node){if(node.isNull())return null;if(!node.isNumber()&&!node.isTextual())throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","价格格式无效");try{BigDecimal n=new BigDecimal(node.asText());if(n.signum()<0||n.scale()>2||n.precision()>18)throw new NumberFormatException();return n;}catch(NumberFormatException e){throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","价格须为非负且最多两位小数");}}
    public Map<String,Object> update(int id,JsonNode body){if(!body.isObject())throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","请求必须为对象");Map<String,Object> old=get(id);BigDecimal min=body.has("minPrice")?decimal(body.get("minPrice")):old.get("minPrice")==null?null:new BigDecimal((String)old.get("minPrice"));BigDecimal max=body.has("maxPrice")?decimal(body.get("maxPrice")):old.get("maxPrice")==null?null:new BigDecimal((String)old.get("maxPrice"));if(min!=null&&max!=null&&min.compareTo(max)>=0)throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","低价必须小于高价");if(mapper.prices(id,min,max)!=1)throw error(HttpStatus.CONFLICT,"CONFIG_NOT_SAVED","阈值未写入，请重试");return get(id);}
}
