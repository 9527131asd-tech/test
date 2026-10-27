package cn.edu.lab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication @EnableScheduling
public class LabSystemApplication {
    public static void main(String[] args) { SpringApplication.run(LabSystemApplication.class, args); }

    // Seed accounts make the demo immediately usable. Change these passwords before deployment.
    @Bean CommandLineRunner seed(JdbcTemplate db, PasswordEncoder encoder) {
        return args -> {
            String[][] accounts = {
                {"student01", "学生示例", "STUDENT", "Student@123"},
                {"teacher01", "教师示例", "TEACHER", "Teacher@123"},
                {"labadmin", "实验室管理员", "LAB_MANAGER", "LabAdmin@123"},
                {"maintainer", "设备维护员", "MAINTAINER", "Repair@123"},
                {"admin", "系统管理员", "ADMIN", "Admin@123"}
            };
            for (String[] a : accounts) db.update("INSERT IGNORE INTO users(user_id,name,user_type,password_hash,department) VALUES(?,?,?,?,?)",
                    a[0], a[1], a[2], encoder.encode(a[3]), "示例院系");
            db.update("INSERT IGNORE INTO laboratories(lab_id,lab_code,lab_name,lab_type,location,capacity,description) VALUES(1,'LAB-001','智能计算实验室','计算机实验室','工科楼301',40,'计算机与人工智能教学科研')");
            db.update("INSERT IGNORE INTO laboratories(lab_id,lab_code,lab_name,lab_type,location,capacity,description) VALUES(2,'LAB-002','基础物理实验室','物理实验室','实验楼205',24,'基础物理实验')");
            db.update("INSERT IGNORE INTO equipment(equip_id,equip_code,equip_name,model,lab_id,status) VALUES(1,'EQ-001','高性能工作站','WS-01',1,'正常')");
            db.update("INSERT IGNORE INTO equipment(equip_id,equip_code,equip_name,model,lab_id,status) VALUES(2,'EQ-002','示波器','OSC-02',2,'正常')");
            db.update("INSERT IGNORE INTO consumables(consumable_id,consumable_code,consumable_name,type,lab_id,stock_quantity,warning_threshold,danger_level) VALUES(1,'CS-001','实验导线','电子耗材',2,30,8,'无')");
        };
    }
}
