package com.goldpricemonitor.api.mapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.*;
@Mapper public interface AuthSessionMapper {
    @Insert("INSERT INTO auth_session(id,principal_id,role,expires_at) VALUES (#{sid},#{id},#{role},#{expires})") int insert(@Param("sid") String sid,@Param("id") int id,@Param("role") String role,@Param("expires") LocalDateTime expires);
    @Select("SELECT * FROM auth_session WHERE id=#{sid} AND principal_id=#{id} AND role=#{role}") Map<String,Object> find(@Param("sid") String sid,@Param("id") int id,@Param("role") String role);
    @Update("UPDATE auth_session SET revoked=true WHERE id=#{sid} AND revoked=false") int revokeActive(String sid);
    @Update("UPDATE auth_session SET revoked=true WHERE id=#{sid}") int revoke(String sid);
    @Update("UPDATE auth_session SET revoked=true WHERE role='USER' AND principal_id=#{id}") int revokeUser(int id);
}
