package com.goldpricemonitor.api.mapper;
import java.util.Map;
import org.apache.ibatis.annotations.*;
@Mapper public interface AdminAccountMapper {
    @Select("SELECT * FROM admin_account WHERE username=#{username}") Map<String,Object> byUsername(String username);
    @Select("SELECT * FROM admin_account WHERE id=#{id} AND status=1") Map<String,Object> activeById(int id);
    @Insert("INSERT INTO admin_account(username,password_hash) VALUES (#{username},#{hash})") int insert(@Param("username") String username,@Param("hash") String hash);
}
