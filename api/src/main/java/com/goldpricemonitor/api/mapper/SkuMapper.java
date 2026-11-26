package com.goldpricemonitor.api.mapper;
import java.util.*;
import org.apache.ibatis.annotations.*;
@Mapper public interface SkuMapper {
    String FILTER="<if test='name != null and name != \"\"'> AND name LIKE CONCAT('%',#{name},'%')</if><if test='status != null'> AND status=#{status}</if>";
    @Select("SELECT id,name,price,status,sort_order AS sortOrder,create_time AS createTime FROM sku WHERE id=#{id}") Map<String,Object> byId(int id);
    @Select("<script>SELECT count(*) FROM sku WHERE 1=1 "+FILTER+"</script>") long count(@Param("name") String name,@Param("status") Integer status);
    @Select("<script>SELECT id,name,price,status,sort_order AS sortOrder,create_time AS createTime FROM sku WHERE 1=1 "+FILTER+" ORDER BY sortOrder ASC,id ASC LIMIT 10 OFFSET #{offset}</script>") List<Map<String,Object>> list(@Param("name") String name,@Param("status") Integer status,@Param("offset") int offset);
    @Insert("INSERT INTO sku(name,price,status,sort_order) VALUES (#{name},#{price},#{status},#{sortOrder})") @Options(useGeneratedKeys=true,keyProperty="id") int insert(Map<String,Object> row);
    @Update("UPDATE sku SET name=#{name},price=#{price},status=#{status},sort_order=#{sortOrder} WHERE id=#{id}") int update(@Param("id") int id,@Param("name") String name,@Param("price") int price,@Param("status") int status,@Param("sortOrder") int sortOrder);
    @Delete("DELETE FROM sku WHERE id=#{id}") int delete(int id);
}
