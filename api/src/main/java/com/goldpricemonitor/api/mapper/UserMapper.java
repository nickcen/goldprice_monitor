package com.goldpricemonitor.api.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {
    @Insert("INSERT INTO `user`(phone,password_hash) VALUES (#{phone},#{hash})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Map<String,Object> row);
    @Select("SELECT * FROM `user` WHERE phone=#{phone}") Map<String,Object> byPhone(String phone);
    @Select("SELECT * FROM `user` WHERE id=#{id}") Map<String,Object> byId(int id);
    @Select("SELECT u.id,u.phone,c.level,u.status,u.valid_from_date AS validFromDate,u.valid_to_date AS validToDate,u.create_time AS createTime FROM `user` u LEFT JOIN user_config c ON c.user_id=u.id WHERE u.id=#{id}") Map<String,Object> detail(int id);
    @Select("<script>SELECT count(*) FROM `user` u LEFT JOIN user_config c ON c.user_id=u.id WHERE 1=1 <if test='phone != null and phone != \"\"'>AND u.phone LIKE CONCAT('%',#{phone},'%')</if><if test='status != null'>AND u.status=#{status}</if></script>") long count(@Param("phone") String phone,@Param("status") Integer status);
    @Select("<script>SELECT u.id,u.phone,c.level,u.status,u.valid_from_date AS validFromDate,u.valid_to_date AS validToDate,u.create_time AS createTime FROM `user` u LEFT JOIN user_config c ON c.user_id=u.id WHERE 1=1 <if test='phone != null and phone != \"\"'>AND u.phone LIKE CONCAT('%',#{phone},'%')</if><if test='status != null'>AND u.status=#{status}</if> ORDER BY u.create_time DESC,u.id DESC LIMIT 10 OFFSET #{offset}</script>") List<Map<String,Object>> list(@Param("phone") String phone,@Param("status") Integer status,@Param("offset") int offset);
    @Update("UPDATE `user` SET password_hash=#{hash} WHERE id=#{id}") int password(@Param("id") int id,@Param("hash") String hash);
    @Update("UPDATE `user` SET status=#{status} WHERE id=#{id}") int status(@Param("id") int id,@Param("status") int status);
    @Update("UPDATE `user` SET valid_from_date=#{from},valid_to_date=#{to} WHERE id=#{id}") int validity(@Param("id") int id,@Param("from") java.time.LocalDate from,@Param("to") java.time.LocalDate to);
}
