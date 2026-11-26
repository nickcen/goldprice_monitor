package com.goldpricemonitor.api.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goldpricemonitor.api.entity.Sku;
@Mapper public interface SkuMapper extends BaseMapper<Sku> {
    String FILTER="<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%',#{name},'%')</if><if test='status != null'> AND status=#{status}</if>";
    @Select("SELECT id,name,price,status,sort_order AS sortOrder,create_time AS createTime FROM sku WHERE id=#{id}") Sku byId(int id);
    @Select("<script>SELECT count(*) FROM sku WHERE 1=1 "+FILTER+"</script>") long count(@Param("name") String name,@Param("status") Integer status);
    @Select("<script>SELECT id,name,price,status,sort_order AS sortOrder,create_time AS createTime FROM sku WHERE 1=1 "+FILTER+" ORDER BY sortOrder ASC,id ASC LIMIT 10 OFFSET #{offset}</script>") List<Sku> list(@Param("name") String name,@Param("status") Integer status,@Param("offset") int offset);
    @Update("UPDATE sku SET name=#{name},price=#{price},status=#{status},sort_order=#{sortOrder} WHERE id=#{id}") int updateSku(@Param("id") int id,@Param("name") String name,@Param("price") int price,@Param("status") int status,@Param("sortOrder") int sortOrder);
    @Delete("DELETE FROM sku WHERE id=#{id}") int deleteSku(int id);
}
