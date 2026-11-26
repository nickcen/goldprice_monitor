package com.goldpricemonitor.api.mapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.goldpricemonitor.api.entity.AuthSession;
@Mapper public interface AuthSessionMapper extends BaseMapper<AuthSession> {
    @Select("SELECT * FROM auth_session WHERE id=#{sid} AND principal_id=#{id} AND role=#{role}") AuthSession find(@Param("sid") String sid,@Param("id") int id,@Param("role") String role);
    @Update("UPDATE auth_session SET revoked=true WHERE id=#{sid} AND revoked=false") int revokeActive(String sid);
    @Update("UPDATE auth_session SET revoked=true WHERE id=#{sid}") int revoke(String sid);
    @Update("UPDATE auth_session SET revoked=true WHERE role='USER' AND principal_id=#{id}") int revokeUser(int id);
}
