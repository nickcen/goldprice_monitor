package com.goldpricemonitor.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String phone;
    private String passwordHash;
    private Integer status;
    private LocalDate validFromDate;
    private LocalDate validToDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private Integer level;

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDate getValidFromDate() { return validFromDate; }
    public void setValidFromDate(LocalDate validFromDate) { this.validFromDate = validFromDate; }
    public LocalDate getValidToDate() { return validToDate; }
    public void setValidToDate(LocalDate validToDate) { this.validToDate = validToDate; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
