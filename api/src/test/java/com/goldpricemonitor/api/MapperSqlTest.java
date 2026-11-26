package com.goldpricemonitor.api;
import com.goldpricemonitor.api.mapper.*;
import com.goldpricemonitor.api.entity.*;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import java.sql.DriverManager;
import java.util.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapperSqlTest {
    @Test void generatedKeysAndFilteredPagesExecute() throws Exception {
        JdbcDataSource data=new JdbcDataSource();data.setURL("jdbc:h2:mem:mapper_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var connection=data.getConnection();var statement=connection.createStatement()){
            statement.execute("CREATE TABLE IF NOT EXISTS `user` (id INT AUTO_INCREMENT PRIMARY KEY,phone VARCHAR(30),password_hash VARCHAR(100),status INT DEFAULT 1,valid_from_date DATE,valid_to_date DATE,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE IF NOT EXISTS user_config (user_id INT PRIMARY KEY,level INT DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS sku (id INT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(120),price INT,status INT,sort_order INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE IF NOT EXISTS payment (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,sku_id INT,payment_number VARCHAR(100),thirdparty_number VARCHAR(100),pay_time TIMESTAMP,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
        org.apache.ibatis.session.Configuration config=new MybatisConfiguration(new Environment("test",new JdbcTransactionFactory(),data));config.addMapper(UserMapper.class);config.addMapper(SkuMapper.class);config.addMapper(PaymentMapper.class);
        try(SqlSession session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true)){
            UserMapper users=session.getMapper(UserMapper.class);User user=new User();user.setPhone("+8613800000000");user.setPasswordHash("hash");users.insert(user);assertNotNull(user.getId());assertEquals("hash",users.byId(user.getId()).getPasswordHash());assertEquals("hash",users.selectById(user.getId()).getPasswordHash());assertEquals(1,users.count("138",1));assertEquals(1,users.list("138",1,0).size());
            SkuMapper skus=session.getMapper(SkuMapper.class);Sku sku=new Sku();sku.setName("gold");sku.setPrice(100);sku.setStatus(1);sku.setSortOrder(0);skus.insert(sku);assertNotNull(sku.getId());assertEquals("gold",skus.byId(sku.getId()).getName());assertEquals(1,skus.count("gold",1));assertEquals(1,skus.list("gold",1,0).size());
            int userId=user.getId(),skuId=sku.getId();
            session.getConnection().createStatement().execute("INSERT INTO payment(user_id,sku_id,payment_number,thirdparty_number,pay_time) VALUES ("+userId+","+skuId+",'pay-1','third-1',CURRENT_TIMESTAMP)");
            PaymentMapper payments=session.getMapper(PaymentMapper.class);assertEquals(1,payments.count(userId,"pay-1",null,null,null,null));assertEquals("gold",payments.list(userId,"pay-1",null,null,null,null,0).getFirst().getSkuName());assertEquals(1,payments.countBySku(skuId));
        }
    }
}
