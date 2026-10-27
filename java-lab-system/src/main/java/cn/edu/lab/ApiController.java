package cn.edu.lab;

import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class ApiController {
    private final JdbcTemplate db;
    public ApiController(JdbcTemplate db) { this.db = db; }

    @GetMapping("/labs") public List<Map<String,Object>> labs(@RequestParam(required=false) String type, @RequestParam(required=false) String q) {
        return db.queryForList("SELECT * FROM laboratories WHERE (? IS NULL OR lab_type=?) AND (? IS NULL OR lab_name LIKE CONCAT('%',?,'%') OR location LIKE CONCAT('%',?,'%')) ORDER BY lab_id", type,type,q,q,q);
    }
    @PostMapping("/labs") @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')")
    public Map<String,Object> addLab(@RequestBody Map<String,Object> x) {
        db.update("INSERT INTO laboratories(lab_code,lab_name,lab_type,location,capacity,open_time,description) VALUES(?,?,?,?,?,?,?)", x.get("code"),x.get("name"),x.get("type"),x.get("location"),x.get("capacity"),x.getOrDefault("openTime","周一至周日 08:00-22:00"),x.get("description"));
        return Map.of("message","实验室已创建");
    }
    @GetMapping("/equipment") public List<Map<String,Object>> equipment(@RequestParam(required=false) Long labId) {
        return labId == null ? db.queryForList("SELECT e.*,l.lab_name FROM equipment e JOIN laboratories l ON e.lab_id=l.lab_id ORDER BY e.equip_id") : db.queryForList("SELECT * FROM equipment WHERE lab_id=? ORDER BY equip_id",labId);
    }
    @PostMapping("/equipment") @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')")
    public Map<String,Object> addEquipment(@RequestBody Map<String,Object> x) {
        db.update("INSERT INTO equipment(equip_code,equip_name,model,specifications,lab_id,purchase_date,use_instruction) VALUES(?,?,?,?,?,?,?)",x.get("code"),x.get("name"),x.get("model"),x.get("specifications"),x.get("labId"),x.get("purchaseDate"),x.get("instruction")); return Map.of("message","设备已登记");
    }

    public record Booking(long labId, List<Long> equipmentIds, LocalDateTime startTime, LocalDateTime endTime) {}
    @PostMapping("/reservations") @Transactional
    public Map<String,Object> book(@RequestBody Booking b, Authentication auth) {
        if (b.startTime()==null || b.endTime()==null || !b.endTime().isAfter(b.startTime()) || b.startTime().isBefore(LocalDateTime.now())) bad("预约时间无效");
        String role = db.queryForObject("SELECT user_type FROM users WHERE user_id=?",String.class,auth.getName());
        boolean teacher = "TEACHER".equals(role) || "ADMIN".equals(role);
        long maxHours = teacher ? 8 : 4; int maxEquipment = teacher ? 100 : 3;
        if (Duration.between(b.startTime(),b.endTime()).toHours()>maxHours || Duration.between(b.startTime(),b.endTime()).toMinutes()>maxHours*60) bad("预约时长超过角色限制");
        List<Long> ids = b.equipmentIds()==null ? List.of() : b.equipmentIds().stream().distinct().toList();
        if (ids.size()>maxEquipment) bad("所选设备数量超过角色限制");
        // Serialize requests from the same account so two simultaneous requests cannot bypass the one-booking rule.
        db.queryForMap("SELECT user_id FROM users WHERE user_id=? FOR UPDATE",auth.getName());
        Map<String,Object> user = db.queryForMap("SELECT violation_count,ban_until,account_status FROM users WHERE user_id=?",auth.getName());
        if (!"正常".equals(user.get("account_status")) || (user.get("ban_until")!=null && ((java.sql.Timestamp)user.get("ban_until")).toLocalDateTime().isAfter(LocalDateTime.now()))) bad("账号当前不可预约");
        // Serialize bookings for the same room. This protects the overlap check against simultaneous requests.
        db.queryForMap("SELECT lab_id FROM laboratories WHERE lab_id=? FOR UPDATE",b.labId());
        if (!"空闲".equals(db.queryForObject("SELECT status FROM laboratories WHERE lab_id=?",String.class,b.labId()))) bad("实验室当前不可预约");
        Integer overlap = db.queryForObject("SELECT COUNT(*) FROM reservations WHERE lab_id=? AND status IN ('待使用','使用中') AND start_time < ? AND end_time > ?",Integer.class,b.labId(),b.endTime(),b.startTime());
        if (overlap!=null && overlap>0) bad("该时段实验室已被预约");
        if (!teacher && db.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=? AND status IN ('待使用','使用中') AND end_time>NOW()",Integer.class,auth.getName())>0) bad("学生同时只能有一个有效预约");
        for (Long id:ids) {
            Map<String,Object> e = db.queryForMap("SELECT lab_id,status FROM equipment WHERE equip_id=? FOR UPDATE",id);
            if (!Objects.equals(((Number)e.get("lab_id")).longValue(),b.labId()) || !"正常".equals(e.get("status"))) bad("设备不属于所选实验室或当前不可用");
            Integer ec = db.queryForObject("SELECT COUNT(*) FROM reservation_equipment re JOIN reservations r ON r.reservation_id=re.reservation_id WHERE re.equip_id=? AND r.status IN ('待使用','使用中') AND r.start_time < ? AND r.end_time > ?",Integer.class,id,b.endTime(),b.startTime());
            if (ec!=null && ec>0) bad("设备时段冲突: "+id);
        }
        KeyHolder key = new GeneratedKeyHolder();
        db.update(c -> { var ps=c.prepareStatement("INSERT INTO reservations(user_id,lab_id,start_time,end_time) VALUES(?,?,?,?)",new String[]{"reservation_id"}); ps.setString(1,auth.getName()); ps.setLong(2,b.labId()); ps.setObject(3,b.startTime()); ps.setObject(4,b.endTime()); return ps; },key);
        long reservationId=Objects.requireNonNull(key.getKey()).longValue();
        for(Long id:ids) db.update("INSERT INTO reservation_equipment(reservation_id,equip_id) VALUES(?,?)",reservationId,id);
        log(auth.getName(),"预约实验室","预约编号="+reservationId);
        return Map.of("reservationId",reservationId,"status","待使用","message","预约成功");
    }
    @GetMapping("/reservations") public List<Map<String,Object>> reservations(Authentication a) {
        return db.queryForList("SELECT r.*,l.lab_name,u.name FROM reservations r JOIN laboratories l ON l.lab_id=r.lab_id JOIN users u ON u.user_id=r.user_id WHERE r.user_id=? OR ? IN ('ADMIN','LAB_MANAGER','TEACHER') ORDER BY r.start_time DESC",a.getName(),role(a));
    }
    @PostMapping("/reservations/{id}/cancel") @Transactional public Map<String,Object> cancel(@PathVariable long id, Authentication a) {
        Map<String,Object> r=db.queryForMap("SELECT * FROM reservations WHERE reservation_id=? FOR UPDATE",id);
        if (!a.getName().equals(r.get("user_id")) && !isManager(a)) forbidden("不能取消他人的预约");
        LocalDateTime start=((java.sql.Timestamp)r.get("start_time")).toLocalDateTime();
        if (start.minusHours(1).isBefore(LocalDateTime.now())) bad("预约开始前1小时内不能取消");
        db.update("UPDATE reservations SET status='已取消' WHERE reservation_id=?",id); log(a.getName(),"取消预约","预约编号="+id); return Map.of("status","已取消");
    }
    @PostMapping("/reservations/{id}/check-in") @Transactional public Map<String,Object> checkIn(@PathVariable long id, Authentication a) {
        int n=db.update("UPDATE reservations SET status='使用中',check_in_time=NOW() WHERE reservation_id=? AND user_id=? AND status='待使用' AND NOW() BETWEEN start_time AND DATE_ADD(start_time,INTERVAL 30 MINUTE)",id,a.getName());
        if(n==0) bad("签到失败：请确认预约属于本人且当前在预约签到时间内"); return Map.of("status","使用中");
    }
    @PostMapping("/reservations/{id}/check-out") @Transactional public Map<String,Object> checkOut(@PathVariable long id, Authentication a) {
        int n=db.update("UPDATE reservations SET status='已使用',check_out_time=NOW() WHERE reservation_id=? AND user_id=? AND status='使用中'",id,a.getName());
        if(n==0) bad("没有可签退的预约"); return Map.of("status","已使用");
    }

    @GetMapping("/repairs") @PreAuthorize("isAuthenticated()") public List<Map<String,Object>> repairs(Authentication a) {
        return isMaintainer(a)||isManager(a) ? db.queryForList("SELECT r.*,e.equip_name FROM repairs r JOIN equipment e ON e.equip_id=r.equip_id ORDER BY r.submit_time DESC") : db.queryForList("SELECT r.*,e.equip_name FROM repairs r JOIN equipment e ON e.equip_id=r.equip_id WHERE r.user_id=? ORDER BY r.submit_time DESC",a.getName());
    }
    @PostMapping("/repairs") @Transactional public Map<String,Object> repair(@RequestBody Map<String,Object>x,Authentication a) {
        Long id=Long.valueOf(x.get("equipmentId").toString()); db.update("INSERT INTO repairs(equip_id,user_id,fault_description,fault_image) VALUES(?,?,?,?)",id,a.getName(),x.get("description"),x.get("image"));
        db.update("UPDATE equipment SET status='故障' WHERE equip_id=? AND status<>'报废'",id); log(a.getName(),"设备报修","设备编号="+id); return Map.of("message","报修已提交");
    }
    @PostMapping("/repairs/{id}/assign") @PreAuthorize("hasAnyRole('ADMIN','MAINTAINER')") @Transactional public Map<String,Object> assignRepair(@PathVariable long id,Authentication a) {
        db.update("UPDATE repairs SET maintainer_id=?,repair_status='处理中' WHERE repair_id=? AND repair_status='待处理'",a.getName(),id); return Map.of("status","处理中");
    }
    @PostMapping("/repairs/{id}/finish") @PreAuthorize("hasAnyRole('ADMIN','MAINTAINER')") @Transactional public Map<String,Object> finishRepair(@PathVariable long id,@RequestBody Map<String,Object>x,Authentication a) {
        Map<String,Object> r=db.queryForMap("SELECT equip_id FROM repairs WHERE repair_id=?",id);
        db.update("UPDATE repairs SET repair_status='已完成',repair_content=?,finish_time=NOW(),maintainer_id=? WHERE repair_id=?",x.get("content"),a.getName(),id);
        db.update("UPDATE equipment SET status='正常',last_maintenance_time=NOW() WHERE equip_id=?",r.get("equip_id")); log(a.getName(),"完成维修","维修单="+id); return Map.of("status","已完成");
    }

    @GetMapping("/consumables") public List<Map<String,Object>> consumables() { return db.queryForList("SELECT c.*,l.lab_name,(c.stock_quantity<=c.warning_threshold) AS low_stock FROM consumables c JOIN laboratories l ON l.lab_id=c.lab_id ORDER BY c.consumable_id"); }
    @PostMapping("/consumables") @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')") public Map<String,Object> addConsumable(@RequestBody Map<String,Object>x) {
        db.update("INSERT INTO consumables(consumable_code,consumable_name,type,specifications,lab_id,stock_quantity,warning_threshold,danger_level,expiry_date) VALUES(?,?,?,?,?,?,?,?,?)",x.get("code"),x.get("name"),x.get("type"),x.get("specifications"),x.get("labId"),x.getOrDefault("stock",0),x.getOrDefault("threshold",5),x.getOrDefault("dangerLevel","无"),x.get("expiryDate")); return Map.of("message","耗材已登记");
    }
    @PostMapping("/consumables/{id}/issuance") @Transactional public Map<String,Object> requestIssue(@PathVariable long id,@RequestBody Map<String,Integer>x,Authentication a) {
        int qty=x.getOrDefault("quantity",0); if(qty<1) bad("领用数量必须大于0");
        Map<String,Object> item=db.queryForMap("SELECT stock_quantity,danger_level FROM consumables WHERE consumable_id=? FOR UPDATE",id);
        if(((Number)item.get("stock_quantity")).intValue()<qty) bad("库存不足");
        if(Set.of("中","高").contains(item.get("danger_level")) && !isTeacher(a)&&!isManager(a)) forbidden("危险耗材仅限教师或管理员申请");
        db.update("INSERT INTO consumable_issuance(consumable_id,user_id,quantity,approval_status) VALUES(?,?,?,'待审核')",id,a.getName(),qty); return Map.of("status","待审核");
    }
    @GetMapping("/issuances") public List<Map<String,Object>> issuances(Authentication a) {
        return isManager(a) ? db.queryForList("SELECT i.*,c.consumable_name,u.name FROM consumable_issuance i JOIN consumables c ON c.consumable_id=i.consumable_id JOIN users u ON u.user_id=i.user_id ORDER BY i.issuance_time DESC") : db.queryForList("SELECT i.*,c.consumable_name FROM consumable_issuance i JOIN consumables c ON c.consumable_id=i.consumable_id WHERE i.user_id=? ORDER BY i.issuance_time DESC",a.getName());
    }
    @PostMapping("/issuances/{id}/approve") @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')") @Transactional public Map<String,Object> approve(@PathVariable long id,Authentication a) {
        Map<String,Object> issue=db.queryForMap("SELECT * FROM consumable_issuance WHERE issuance_id=? FOR UPDATE",id); long c=((Number)issue.get("consumable_id")).longValue(); int q=((Number)issue.get("quantity")).intValue();
        Map<String,Object> stock=db.queryForMap("SELECT stock_quantity FROM consumables WHERE consumable_id=? FOR UPDATE",c);
        if(((Number)stock.get("stock_quantity")).intValue()<q) bad("审批失败：当前库存不足");
        int changed=db.update("UPDATE consumable_issuance SET approval_status='已通过',approver_id=? WHERE issuance_id=? AND approval_status='待审核'",a.getName(),id);
        if(changed==0) bad("该申请已处理");
        db.update("UPDATE consumables SET stock_quantity=stock_quantity-? WHERE consumable_id=?",q,c); log(a.getName(),"审批耗材领用","领用单="+id); return Map.of("status","已通过");
    }
    @GetMapping("/reports/summary") @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER','TEACHER')") public Map<String,Object> summary() {
        return Map.of("todayReservations",db.queryForObject("SELECT COUNT(*) FROM reservations WHERE DATE(created_at)=CURDATE()",Integer.class),
            "activeRepairs",db.queryForObject("SELECT COUNT(*) FROM repairs WHERE repair_status IN ('待处理','处理中')",Integer.class),
            "equipmentCount",db.queryForObject("SELECT COUNT(*) FROM equipment",Integer.class),
            "faultyEquipment",db.queryForObject("SELECT COUNT(*) FROM equipment WHERE status IN ('故障','维修')",Integer.class),
            "lowStockItems",db.queryForObject("SELECT COUNT(*) FROM consumables WHERE stock_quantity<=warning_threshold",Integer.class));
    }
    @GetMapping("/reports") public List<Map<String,Object>> reports(Authentication a) {
        return isTeacher(a)||isManager(a) ? db.queryForList("SELECT r.*,u.name student_name FROM experiment_reports r JOIN users u ON u.user_id=r.user_id ORDER BY r.submit_time DESC") : db.queryForList("SELECT * FROM experiment_reports WHERE user_id=? ORDER BY submit_time DESC",a.getName());
    }
    @PostMapping("/uploads") public Map<String,Object> upload(@RequestPart("file") MultipartFile file) {
        if(file.isEmpty()) bad("请选择文件");
        String original=Path.of(Objects.requireNonNullElse(file.getOriginalFilename(),"report")).getFileName().toString();
        String suffix=original.contains(".")?original.substring(original.lastIndexOf('.')):".bin";
        if(!Set.of(".pdf",".doc",".docx").contains(suffix.toLowerCase())) bad("仅支持 PDF、DOC 或 DOCX 文件");
        try {
            Path dir=Path.of("uploads").toAbsolutePath().normalize(); Files.createDirectories(dir);
            String stored=UUID.randomUUID()+suffix.toLowerCase(); Files.copy(file.getInputStream(),dir.resolve(stored),StandardCopyOption.REPLACE_EXISTING);
            return Map.of("filePath","/files/"+stored,"originalName",original);
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败"); }
    }
    @PostMapping("/reports") public Map<String,Object> submitReport(@RequestBody Map<String,Object>x,Authentication a) {
        db.update("INSERT INTO experiment_reports(report_title,user_id,lab_id,file_path) VALUES(?,?,?,?)",x.get("title"),a.getName(),x.get("labId"),x.get("filePath")); log(a.getName(),"提交实验报告",String.valueOf(x.get("title"))); return Map.of("message","报告已提交");
    }
    @PostMapping("/reports/{id}/review") @PreAuthorize("hasAnyRole('ADMIN','TEACHER')") public Map<String,Object> review(@PathVariable long id,@RequestBody Map<String,Object>x,Authentication a) {
        double score=Double.parseDouble(x.get("score").toString()); if(score<0||score>100) bad("分数范围应为0至100");
        db.update("UPDATE experiment_reports SET score=?,comment=?,teacher_id=?,review_time=NOW() WHERE report_id=?",score,x.get("comment"),a.getName(),id); log(a.getName(),"批改实验报告","报告编号="+id); return Map.of("message","批改已保存");
    }
    private String role(Authentication a) { return a.getAuthorities().stream().map(x->x.getAuthority().replace("ROLE_","")).findFirst().orElse("STUDENT"); }
    private boolean isManager(Authentication a) { return a.getAuthorities().stream().anyMatch(x->Set.of("ROLE_ADMIN","ROLE_LAB_MANAGER").contains(x.getAuthority())); }
    private boolean isMaintainer(Authentication a) { return a.getAuthorities().stream().anyMatch(x->Set.of("ROLE_ADMIN","ROLE_MAINTAINER").contains(x.getAuthority())); }
    private boolean isTeacher(Authentication a) { return a.getAuthorities().stream().anyMatch(x->Set.of("ROLE_ADMIN","ROLE_TEACHER").contains(x.getAuthority())); }
    private void log(String user,String action,String details) { db.update("INSERT INTO operation_logs(user_id,action,details) VALUES(?,?,?)",user,action,details); }
    private static void bad(String s) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,s); }
    private static void forbidden(String s) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,s); }
}
