package com.goldpricemonitor.api;
import com.goldpricemonitor.api.mapper.*;
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
            statement.execute("CREATE TABLE IF NOT EXISTS `user` (id INT AUTO_INCREMENT PRIMARY KEY,phone VARCHAR(30),password_hash VARCHAR(100),status INT DEFAULT 1,valid_from_date DATE,valid_to_date DATE,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE IF NOT EXISTS user_config (user_id INT PRIMARY KEY,level INT DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS sku (id INT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(120),price INT,status INT,sort_order INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE IF NOT EXISTS payment (id INT AUTO_INCREMENT PRIMARY KEY,user_id INT,sku_id INT,payment_number VARCHAR(100),thirdparty_number VARCHAR(100),pay_time TIMESTAMP,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
        org.apache.ibatis.session.Configuration config=new org.apache.ibatis.session.Configuration(new Environment("test",new JdbcTransactionFactory(),data));config.addMapper(UserMapper.class);config.addMapper(SkuMapper.class);config.addMapper(PaymentMapper.class);
        try(SqlSession session=new SqlSessionFactoryBuilder().build(config).openSession(true)){
            UserMapper users=session.getMapper(UserMapper.class);Map<String,Object> user=new HashMap<>();user.put("phone","+8613800000000");user.put("hash","hash");users.insert(user);assertNotNull(user.get("id"));assertEquals(1,users.count("138",1));assertEquals(1,users.list("138",1,0).size());
            SkuMapper skus=session.getMapper(SkuMapper.class);Map<String,Object> sku=new HashMap<>();sku.put("name","gold");sku.put("price",100);sku.put("status",1);sku.put("sortOrder",0);skus.insert(sku);assertNotNull(sku.get("id"));assertEquals(1,skus.count("gold",1));assertEquals(1,skus.list("gold",1,0).size());
            int userId=((Number)user.get("id")).intValue(),skuId=((Number)sku.get("id")).intValue();
            session.getConnection().createStatement().execute("INSERT INTO payment(user_id,sku_id,payment_number,thirdparty_number,pay_time) VALUES ("+userId+","+skuId+",'pay-1','third-1',CURRENT_TIMESTAMP)");
            PaymentMapper payments=session.getMapper(PaymentMapper.class);assertEquals(1,payments.count(userId,"pay-1",null,null,null,null));assertEquals(1,payments.list(userId,"pay-1",null,null,null,null,0).size());assertEquals(1,payments.countBySku(skuId));
        }
    }
}
