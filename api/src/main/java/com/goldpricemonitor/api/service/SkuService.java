package com.goldpricemonitor.api.service;
import com.goldpricemonitor.api.mapper.*;
import com.goldpricemonitor.api.entity.Sku;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.goldpricemonitor.api.service.ApiSupport.*;
@Service public class SkuService {
    public record Input(String name,Integer price,Integer status,Integer sortOrder){}
    private final SkuMapper skus;private final PaymentMapper payments;public SkuService(SkuMapper skus,PaymentMapper payments){this.skus=skus;this.payments=payments;}
    public Map<String,Object> get(int id){Sku row=skus.byId(id);if(row==null)throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","商品不存在");return publicRow(row(row));}
    public Map<String,Object> list(String name,Integer status,Integer page){if(status!=null)status(status);int p=page(page);String filter=name==null||name.isBlank()?null:name;return paged(skus.list(filter,status,(p-1)*10).stream().map(this::row).toList(),skus.count(filter,status),p);}
    private Map<String,Object> row(Sku sku){Map<String,Object> out=new LinkedHashMap<>();out.put("id",sku.getId());out.put("name",sku.getName());out.put("price",sku.getPrice());out.put("status",sku.getStatus());out.put("sortOrder",sku.getSortOrder());out.put("createTime",sku.getCreateTime());return out;}
    private void validate(Input b){if(b==null||b.name()==null||b.name().isBlank()||b.name().length()>120||b.price()==null||b.price()<0||b.sortOrder()==null)throw error(HttpStatus.BAD_REQUEST,"INVALID_PARAMETER","商品参数无效");status(b.status());}
    public Map<String,Object> create(Input b){validate(b);Sku row=new Sku();row.setName(b.name().trim());row.setPrice(b.price());row.setStatus(b.status());row.setSortOrder(b.sortOrder());skus.insert(row);return get(row.getId());}
    public Map<String,Object> update(int id,Input b){get(id);validate(b);skus.updateSku(id,b.name().trim(),b.price(),b.status(),b.sortOrder());return get(id);}
    @Transactional public Map<String,Object> delete(int id){get(id);if(payments.countBySku(id)>0)throw error(HttpStatus.CONFLICT,"DELETE_CONFLICT","商品有支付记录，请停用");try{skus.deleteSku(id);}catch(DataIntegrityViolationException e){throw error(HttpStatus.CONFLICT,"DELETE_CONFLICT","商品有支付记录，请停用");}return Map.of("success",true);}
}
