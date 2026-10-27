CREATE TABLE IF NOT EXISTS users (
 user_id VARCHAR(40) PRIMARY KEY, name VARCHAR(80) NOT NULL, user_type VARCHAR(30) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, phone VARCHAR(30), department VARCHAR(100),
 violation_count INT NOT NULL DEFAULT 0, ban_until DATETIME NULL, account_status VARCHAR(20) NOT NULL DEFAULT '正常'
);
CREATE TABLE IF NOT EXISTS laboratories (
 lab_id BIGINT PRIMARY KEY AUTO_INCREMENT, lab_code VARCHAR(30) NOT NULL UNIQUE, lab_name VARCHAR(100) NOT NULL,
 lab_type VARCHAR(40) NOT NULL, location VARCHAR(120) NOT NULL, capacity INT NOT NULL,
 open_time VARCHAR(300) NOT NULL DEFAULT '周一至周日 08:00-22:00', status VARCHAR(20) NOT NULL DEFAULT '空闲', description TEXT,
 INDEX idx_lab_type_location(lab_type, location), INDEX idx_lab_status(status)
);
CREATE TABLE IF NOT EXISTS equipment (
 equip_id BIGINT PRIMARY KEY AUTO_INCREMENT, equip_code VARCHAR(40) NOT NULL UNIQUE, equip_name VARCHAR(100) NOT NULL,
 model VARCHAR(80), specifications VARCHAR(200), lab_id BIGINT NOT NULL, purchase_date DATE,
 status VARCHAR(20) NOT NULL DEFAULT '正常', use_instruction TEXT, last_maintenance_time DATETIME NULL,
 CONSTRAINT fk_equipment_lab FOREIGN KEY(lab_id) REFERENCES laboratories(lab_id), INDEX idx_equipment_lab_status(lab_id,status)
);
CREATE TABLE IF NOT EXISTS reservations (
 reservation_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id VARCHAR(40) NOT NULL, lab_id BIGINT NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, status VARCHAR(20) NOT NULL DEFAULT '待使用',
 check_in_time DATETIME NULL, check_out_time DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_res_user FOREIGN KEY(user_id) REFERENCES users(user_id), CONSTRAINT fk_res_lab FOREIGN KEY(lab_id) REFERENCES laboratories(lab_id),
 INDEX idx_res_lab_time(lab_id,status,start_time,end_time), INDEX idx_res_user_status(user_id,status)
);
CREATE TABLE IF NOT EXISTS reservation_equipment (
 reservation_id BIGINT NOT NULL, equip_id BIGINT NOT NULL, PRIMARY KEY(reservation_id,equip_id),
 CONSTRAINT fk_re_res FOREIGN KEY(reservation_id) REFERENCES reservations(reservation_id), CONSTRAINT fk_re_equip FOREIGN KEY(equip_id) REFERENCES equipment(equip_id)
);
CREATE TABLE IF NOT EXISTS repairs (
 repair_id BIGINT PRIMARY KEY AUTO_INCREMENT, equip_id BIGINT NOT NULL, user_id VARCHAR(40) NOT NULL, maintainer_id VARCHAR(40),
 submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, fault_description TEXT NOT NULL, fault_image VARCHAR(500),
 repair_status VARCHAR(20) NOT NULL DEFAULT '待处理', repair_content TEXT, finish_time DATETIME NULL,
 CONSTRAINT fk_repair_equip FOREIGN KEY(equip_id) REFERENCES equipment(equip_id), INDEX idx_repair_status(repair_status,submit_time)
);
CREATE TABLE IF NOT EXISTS consumables (
 consumable_id BIGINT PRIMARY KEY AUTO_INCREMENT, consumable_code VARCHAR(40) NOT NULL UNIQUE, consumable_name VARCHAR(100) NOT NULL,
 type VARCHAR(40), specifications VARCHAR(200), lab_id BIGINT NOT NULL, stock_quantity INT NOT NULL DEFAULT 0,
 warning_threshold INT NOT NULL DEFAULT 5, danger_level VARCHAR(20) NOT NULL DEFAULT '无', expiry_date DATE NULL,
 CONSTRAINT fk_consumable_lab FOREIGN KEY(lab_id) REFERENCES laboratories(lab_id), INDEX idx_stock_warning(stock_quantity,warning_threshold)
);
CREATE TABLE IF NOT EXISTS consumable_issuance (
 issuance_id BIGINT PRIMARY KEY AUTO_INCREMENT, consumable_id BIGINT NOT NULL, user_id VARCHAR(40) NOT NULL,
 issuance_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, quantity INT NOT NULL, return_time DATETIME NULL,
 approver_id VARCHAR(40), approval_status VARCHAR(20) NOT NULL DEFAULT '待审核',
 CONSTRAINT fk_issue_item FOREIGN KEY(consumable_id) REFERENCES consumables(consumable_id), INDEX idx_issue_status(approval_status,issuance_time)
);
CREATE TABLE IF NOT EXISTS experiment_reports (
 report_id BIGINT PRIMARY KEY AUTO_INCREMENT, report_title VARCHAR(150) NOT NULL, user_id VARCHAR(40) NOT NULL,
 lab_id BIGINT NOT NULL, file_path VARCHAR(500) NOT NULL, submit_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 score DECIMAL(5,2), comment TEXT, teacher_id VARCHAR(40), review_time DATETIME NULL,
 CONSTRAINT fk_report_lab FOREIGN KEY(lab_id) REFERENCES laboratories(lab_id), INDEX idx_report_user(user_id,submit_time)
);
CREATE TABLE IF NOT EXISTS operation_logs (
 log_id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id VARCHAR(40), action VARCHAR(120) NOT NULL,
 details VARCHAR(1000), created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_log_created(created_at)
);
