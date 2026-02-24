package com.finansserver;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonTypeInfo.None;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

//paralel islemlerle bagimliliklari kurma -> mvn clean install -T 1C
//baslatma -> mvn spring-boot:run

@RestController
@SpringBootApplication
public class Main {

    //Console Color
    public static final String RESET = "\033[0m"; 
    public static final String GREEN = "\033[32m"; 
    public static final String YELLOW = "\033[33m"; 
    public static final String RED = "\033[31m";

    //Database Informations
    private static final String URL = "jdbc:postgresql://localhost:5432/finansServer";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root";


    //ErrorCodes
    public static Map<Integer, String> errorMap = new HashMap<>();;

    //sessions
    public static Map<HttpSession, Integer> sessionOpenedUser = new HashMap<>();;

    static { // Parse error codes
        try (BufferedReader br = new BufferedReader(new FileReader("middlewareErrorCodesNew.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                
                // Boş satırları ve yorum satırlarını atla
                if (line.isEmpty() || !line.startsWith("//")) continue;
                
                //200 -> course created succesfully  formatını temizle
                line = line.substring(2).trim(); // "//" işaretini kaldır
                
                // "200 -> course created succesfully" formatını ayır
                String[] parts = line.split("->");
                if (parts.length == 2) {
                    try {
                        int code = Integer.parseInt(parts[0].trim());
                        String message = parts[1].trim();
                        errorMap.put(code, message);
                    } catch (NumberFormatException e) {
                        System.err.println("Geçersiz hata kodu: " + parts[0]);
                    }
                }
            }
            System.out.println("Error Codes Succesfully Parsed");
        } catch (IOException e) {
            System.err.println("Dosya okuma hatası: " + e.getMessage());
        }
    }

    // Connection
    private static Connection conn;

    //course permissions
    public static final List<String> createCoursePermissions = new ArrayList<>();
    static{ 
        createCoursePermissions.add("admin");
    }

    public static final List<String> createLocalADPermissions = new ArrayList<>();
    static{ 
        createLocalADPermissions.add("user");
        createLocalADPermissions.add("admin");
    }

    static {// Connecting to db
        try {
            // creating connection
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connected to Database!!!");
        } catch (SQLException e) {
            e.printStackTrace();  // Write error details
        }
    }

    

    public static void main(String[] args){
        SpringApplication.run(Main.class, args); //start middleware
    }
   


    public static String parseForJsonb(String input){
        return "\"" + input + "\"";
    }

    public static List<String> jsonStrToStrList(String jsonData){
        try{
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(jsonData, new TypeReference<List<String>>() {});
        } catch (JsonMappingException e) {
            List<String> datas = new ArrayList<>();
            return datas;
        } catch (JsonProcessingException e) {
            List<String> datas = new ArrayList<>();
            return datas;
        }
    }

    public static String generateActionUrl() {
        try {

            String uuid = UUID.randomUUID().toString();
            
            SecureRandom random = new SecureRandom();
            byte[] randomBytes = new byte[16];
            random.nextBytes(randomBytes);

            String rawData = uuid + Base64.getEncoder().encodeToString(randomBytes);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawData.getBytes(StandardCharsets.UTF_8));

            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

            return encoded.substring(0, 40);

        } catch (Exception e) {
            throw new RuntimeException("Error generating secure action URL", e);
        }
    }

    public static String hashPassword(String password){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));  
            }
            return hexString.toString();
        }catch(NoSuchAlgorithmException e){
            return null;
        }
    }


    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    public static boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(email);
        return matcher.matches();
    }


    private static final String query = "SELECT created_at FROM actions WHERE action_url = ?";
    public static boolean isActionValid(String actionURL) {
        try (PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, actionURL);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Timestamp createdAt = rs.getTimestamp("created_at");

                LocalDateTime createdTime = createdAt.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                LocalDateTime now = LocalDateTime.now();
                long secondsPassed = Duration.between(createdTime, now).getSeconds();

                return secondsPassed <= 120;
            }
        }catch(Exception e){
            System.out.println(RED + "[WARNING] SQL ERROR" + RESET + e);
        }
        return false;
    }


    static String changePasswordQuery = "UPDATE users SET password = ? WHERE uid = ?;";
    public static Integer changePassword(Integer uid, String newPasswordH){
        try(PreparedStatement ps = conn.prepareStatement(changePasswordQuery)){

            ps.setString(1, newPasswordH);
            ps.setInt(2, uid);

            int rowsAffected = ps.executeUpdate();

            if(rowsAffected>0){
                return 108;
            }
            else{
                return 102;
            }
        }catch(Exception e){
            return 105;
        }
    }

    static String updateSeriePasswordQuery = "UPDATE users SET series = ? WHERE uid = ?;";
    public static Integer changeUpdateSeries(Integer uid, Integer newSerie){
        try(PreparedStatement ps = conn.prepareStatement(updateSeriePasswordQuery)){

            ps.setInt(1, newSerie);
            ps.setInt(2, uid);

            int rowsAffected = ps.executeUpdate();

            if(rowsAffected>0){
                return 108;
            }
            else{
                return 102;
            }
        }catch(Exception e){
            return 105;
        }
    }


    static String updateActionQuery = "UPDATE actions SET processed_at = NOW(), processer_ip = ?::inet WHERE action_url = ?";
    public static boolean updateActionProcessedInfo(String url, String processerIp) {
        try (PreparedStatement ps = conn.prepareStatement(updateActionQuery)) {

            ps.setString(1, processerIp); // processer_ip (IP adresi olarak String parametresi)
            ps.setString(2, url); // action_url

            int rowsAffected = ps.executeUpdate();  // updating record in DB
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.out.println(e);
            return false;
        }
    }

    static String getPointQuery = "SELECT points FROM users WHERE uid = ?";
    public static int getUserPoints(int userId) {
        try (PreparedStatement ps = conn.prepareStatement(getPointQuery)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("points");
                }else{
                    return -1;
                }
            }catch (Exception e){
                return -1;
            }
        } catch (SQLException e) {
            return -1;
        }
    }

    static String updatePointQuery = "UPDATE users SET points = ? WHERE uid = ?";
    public static boolean setUserPoints(int userId, int newPoint) {
        
        try (PreparedStatement ps = conn.prepareStatement(getPointQuery)) {
            ps.setInt(1, newPoint);
            ps.setInt(2, userId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    static String checkUsernameQuery = "SELECT COUNT(*) FROM users WHERE username = ?";
    public static Boolean isUsernameTaken(String username) {
        try (PreparedStatement ps = conn.prepareStatement(checkUsernameQuery)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count != 0;  
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }


    static String checkTitleQuery = "SELECT COUNT(*) FROM courses WHERE title = ?";
    public static Boolean isCourseTitleTaken(String title) {
        try (PreparedStatement ps = conn.prepareStatement(checkTitleQuery)) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count == 0;  // Eğer kullanıcı adı varsa, true dönecek
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }

    static String checkLocalADTitleQuery = "SELECT COUNT(*) FROM courses WHERE title = ?";
    public static Boolean isLocalADTitleTaken(String title) {
        try (PreparedStatement ps = conn.prepareStatement(checkLocalADTitleQuery)) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    return count == 0;  // Eğer kullanıcı adı varsa, true dönecek
                }
            }
        } catch (SQLException e) {
            return null;
        }
        return null;
    }


    static String deleteUserQuery = "DELETE FROM users WHERE uid = ?";
    public static boolean deleteUser(int userId) {
        try (PreparedStatement ps = conn.prepareStatement(deleteUserQuery)) {
            ps.setInt(1, userId);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            return false;
        }
    }



    static String userInsertQuery = "INSERT INTO users (username, password, email, balance, role, accupation, created_at) VALUES (?, ?, ?, ?, ?::user_role_enum, ?::user_accupation_enum, CURRENT_TIMESTAMP);";
    public static boolean addUser(String username, String password, String email, Integer balance, String role, String accupation){
        try(PreparedStatement ps = conn.prepareStatement(userInsertQuery);){
            ps.setString(1, username);
            ps.setString(2, password); 
            ps.setString(3, email);    
            ps.setInt(4, balance);    
            ps.setString(5, role);
            ps.setString(6, accupation); 
            int rowsAffected = ps.executeUpdate();  // Adding to DB
            return rowsAffected > 0;
        } catch (SQLException e){
            return false;
        }
        
    }


    static String courseInsertQuery = "INSERT INTO courses (title, link, description, creator_uid, status, created_at) VALUES (?, ?, ?, ?, ?::course_status_enum, CURRENT_TIMESTAMP);";
    public static Integer addCourse(String title, String link, String description, Integer creator_uid, String status){
        try (PreparedStatement coursePS = conn.prepareStatement(courseInsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            coursePS.setString(1, title);
            coursePS.setString(2, link);
            coursePS.setString(3, description);
            coursePS.setInt(4, creator_uid);
            coursePS.setString(5, status);
    
            int rowsAffected = coursePS.executeUpdate(); // Add course to DB
    
            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = coursePS.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1); // Return created course id
                    }
                }
            }
        } catch (SQLException e){
            return 0;
        }
        return 0; // if not successfull return 0
    }
    

    static String localADInsertQuery = "INSERT INTO localads (title, link, description, creator_uid, status, created_at) VALUES (?, ?, ?, ?, ?::course_status_enum, CURRENT_TIMESTAMP);";
    public static Integer addLocalAD(String title, String link, String description, Integer creator_uid, String status){
        try (PreparedStatement coursePS = conn.prepareStatement(localADInsertQuery, Statement.RETURN_GENERATED_KEYS)) {
            coursePS.setString(1, title);
            coursePS.setString(2, link);
            coursePS.setString(3, description);
            coursePS.setInt(4, creator_uid);
            coursePS.setString(5, status);
    
            int rowsAffected = coursePS.executeUpdate(); // Add course to DB
    
            if (rowsAffected > 0) {
                try (ResultSet generatedKeys = coursePS.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1); // Return created course id
                    }
                }
            }
        } catch (SQLException e){
            return 0;
        }
        return 0; // if not successfull return 0
    }
    

    static String addActionQuery = "INSERT INTO actions (action_url, action_type, datas, created_at) VALUES (?, ?::action_type_enum, ?::jsonb, NOW())";
    public static boolean addAction(String url, String type, List<String> datas) {
        try (PreparedStatement ps = conn.prepareStatement(addActionQuery)) {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonDatas = objectMapper.writeValueAsString(datas); // List<String> -> JSON String

            ps.setString(1, url);  // action_url
            ps.setString(2, type); // action_type (enum)
            ps.setString(3, jsonDatas); // JSON string olarak PostgreSQL'e gönder

            System.out.println(GREEN + "[INFO] " + type + " ACTION ADDED: " + jsonDatas + RESET);

            int rowsAffected = ps.executeUpdate();  // Veritabanına ekleme işlemi
            return rowsAffected > 0;
        } catch (Exception e) {
            System.out.println(RED + "[WARNING] ACTION FAILED: " + e.getMessage() + RESET);
            return false;
        }
    }



    public static String getIpFromRequest(HttpServletRequest request){
        String ip = request.getHeader("X-Forwarded-For"); 
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // IPv6 localhost'u IPv4 olarak göster
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }


    static String getPasswordQuery = "SELECT password FROM users WHERE uid = ?";
    public static String getPasswordById(int userId){
        String password = null;

        try (PreparedStatement ps = conn.prepareStatement(getPasswordQuery)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    password = rs.getString("password");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return password; // if user doesnt exist return null
    }
   
    
    static String getMailQuery = "SELECT email FROM users WHERE uid = ?";
    public static String getMailById(int userId){
        String email = null;

        try (PreparedStatement ps = conn.prepareStatement(getMailQuery)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    email = rs.getString("email");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return email; // if user doesnt exist return null
    }


    static String getBalanceQuery = "SELECT balance FROM users WHERE uid = ?";
    public static Integer getBalance(int userId){
        Integer balance = null;

        try (PreparedStatement ps = conn.prepareStatement(getBalanceQuery)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    balance = rs.getInt("balance");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return balance; // if user doesnt exist return null
    }


    static String getRoleQuery = "SELECT role FROM users WHERE uid = ?";
    public static String getRole(int userId){
        String role = null;

        try (PreparedStatement ps = conn.prepareStatement(getRoleQuery)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    role = rs.getString("role");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return role; // if user doesnt exist return null
    }


    static String getUIDFromUsernameQuery = "SELECT uid FROM users WHERE username = ?";
    public static Integer getUIDFromUsername(String username){
        Integer uid = null;

        try (PreparedStatement ps = conn.prepareStatement(getUIDFromUsernameQuery)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    uid = rs.getInt("uid");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return uid;
    }


    static String getUIDFromMailQuery = "SELECT uid FROM users WHERE email = ?";
    public static Integer getUIDFromMail(String email){
        Integer uid = null;

        try (PreparedStatement ps = conn.prepareStatement(getUIDFromMailQuery)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    uid = rs.getInt("uid");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return uid;
    }


    static String getCourseOwnerQuery = "SELECT creator_uid FROM courses WHERE course_id = ?";
    public static Integer getCreator(int courseId){
        Integer CreatorUid = null;

        try (PreparedStatement ps = conn.prepareStatement(getCourseOwnerQuery)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CreatorUid = rs.getInt("creator_uid");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return CreatorUid; //if user doesnt exist return null
    }


    static String getCourseAvabilityQuery = "SELECT status FROM courses WHERE course_id = ?";
    public static String getCourseAvability(int courseId){
        String avability = null;

        try (PreparedStatement ps = conn.prepareStatement(getCourseAvabilityQuery)) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    avability = rs.getString("status");
                }
            }
        } catch (SQLException e){
            return null;
        }
        return avability; // if user doesnt exist return null
    }

    
    static String getDataFromActionQuery = "SELECT datas FROM actions WHERE action_url = ?";
    public static List<String> getDataFromAction(String url) {
        List<String> datasList = new ArrayList<>();

        try{
            PreparedStatement stmt = conn.prepareStatement(getDataFromActionQuery);
            stmt.setString(1, url);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String jsonData = rs.getString("datas");
                datasList = jsonStrToStrList(jsonData);
            }
        } catch (SQLException e) { 
            return datasList;
        }
        
        return datasList;
    }


    static String getActionTypeFromActionQuery = "SELECT action_type FROM actions WHERE action_url = ?";
    public static String getActionTypeFromAction(String url){
        String actionType = null; 

        try (PreparedStatement ps = conn.prepareStatement(getActionTypeFromActionQuery)) {
            ps.setString(1, url);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    actionType = rs.getString("action_type");
                }
            } catch (Exception e) {
                return String.valueOf(e);
            }
        } catch (Exception e) {
            return String.valueOf(e);
        }
        
        return actionType;

    }


    static String getProcessTimeQuery = "SELECT processed_at FROM actions WHERE action_url = ?";
    private Map<String, Object> of;
    public static String getProcessTime(String url){
        String processed_at = null;

        try (PreparedStatement ps = conn.prepareStatement(getProcessTimeQuery)) {
            ps.setString(1, url);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    processed_at = rs.getString("processed_at");
                }
            } catch (Exception e) {
                return e.getMessage();
            }
        } catch (Exception e) {
            return e.getMessage();
        }
        
        return processed_at;

    }




    // GET Request -> http://localhost:8080/action/{input}
    @GetMapping("/action/{action_url}")
    public static int getDoAction(@PathVariable String action_url, HttpServletRequest request) {
      
        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowedWOCount("/action", ip)){
            String action_type = getActionTypeFromAction(action_url);

            if(action_type==null){
                System.out.println(RED + "[WRONG URL] " + ip + RESET);
                RateLimiterService.wrongEntry("/action", ip);
                return 404;
            }

            if(getProcessTime(action_url)!=null){  
                RateLimiterService.wrongEntry("/action", ip); 
                return 402;
            }

            if(!isActionValid(action_url)){
                RateLimiterService.wrongEntry("/action", ip);
                return 403;
            }

            if(!updateActionProcessedInfo(action_url, ip)){
                return 405;
            }

            System.out.println(GREEN + "[INFO] " + action_type + " ACTION GOT TRIGGERED --> " + action_url + RESET);
            
            List<String> input = getDataFromAction(action_url);

            switch(action_type){

                case "createUser":

                    String username = input.get(0);
                    String password = input.get(1);
                    String email = input.get(2);
                    String accupation = input.get(3);

                    if(!isUsernameTaken(username)){
                        if(addUser(username, password, email, 0, "user", accupation)){
                            System.out.println(GREEN + "[INFO] USER CREATED: " + username);
                            return 100;
                        }else{
                            return 105;
                        }
                    }    
                    else{
                        return 107;
                    }

                case "deleteUser":
                    Integer uid = Integer.valueOf(input.get(0));
                    if(deleteUser(uid)){
                        System.out.println(GREEN + "[USER DELETED] " + uid + RESET);
                        return 600;
                    }
                    return 605;

                case "createCourse":
                    String ctitle = input.get(0);
                    String clink = input.get(1);
                    String cdescription = input.get(2);
                    Integer ccreator_uid = Integer.valueOf(input.get(3));
                    //String ccreator_password = input.get(3);
                    if(createCoursePermissions.contains(getRole(ccreator_uid))){
                        if(isCourseTitleTaken(ctitle)){
                            Integer newCourseId = addCourse(ctitle, clink, cdescription, ccreator_uid, "active");
                            if(newCourseId != 0){

                                System.out.println(GREEN + "[INFO] COURCE CREATED: " + ctitle + RESET);
                                
                                return 200;
                            }
                            else{
                                return 205;
                            }
                        }
                        return 206;
                    } else{
                        return 202;
                    }
                
                case "createLocalAD":
                    String title = input.get(0);
                    String link = input.get(1);
                    String description = input.get(2);
                    Integer creator_uid = Integer.valueOf(input.get(3));
                    String creator_passwordH = hashPassword(input.get(4));

                    if(createCoursePermissions.contains(getRole(creator_uid))){
                        if(isLocalADTitleTaken(title)){
                            Integer newCourseId = addLocalAD(title, link, description, creator_uid, "active");
                            if(newCourseId != 0){

                                System.out.println(GREEN + "[INFO] LOCAL AD CREATED: " + title + RESET);
                                
                                return 300;
                            }
                            else{
                                return 305;
                            }
                        }
                        return 306;
                    } else{
                        return 302;
                    }

        
                case "changeUserPassword":
                    Integer changePasswordUid = Integer.valueOf(input.get(0));
                    String newPasswordH = input.get(1);
                    return changePassword(changePasswordUid, newPasswordH);

                default: 
                    return 999;
        
            }    
        }else{
            return 429;
        } 
    }

    // POST Request → http://localhost:8080/user/create
    @PostMapping("/user/create")
    public static Integer createUser(@RequestBody List<String> input) throws SQLException{
        if (input.size() == 4) {
            try{
                String username = input.get(0);
                String passwordH = hashPassword(input.get(1));
                input.set(1, passwordH);
                String email = input.get(2);
                String accupation = input.get(3);
                

                if(!isUsernameTaken(username)){
                    if(username!=null & passwordH!=null & isValidEmail(email)){
                        String actionURL = generateActionUrl();
                        
                        if(addAction(actionURL, "createUser", input)){
                            if(emailSender.sendMail(email, actionURL, "Create User??")){
                                return 110;
                            }
                            return 701;
                        }
                        else{
                            return 105;
                        }
                    }else{
                        return 103;
                    }
                }
                else{
                    return 106;
                }
            } catch (Exception e){
                return 999;
            }
        }
        else{
            return 103;
        }
    }

    // POST Request → http://localhost:8080/course/create
    // ex: curl -X POST "http://localhost:8080/course/create" ^-H "Content-Type: application/json" ^ -d "[\"My Course Title\", \"https://example.com/file\", \"1\", \"h\"]" 
    @PostMapping("/course/create") 
    public static Integer createCourse(@RequestBody List<String> input, HttpServletRequest request, HttpSession Session) throws SQLException{
        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowedWOCount("/user/passwordCorrection", ip)){
            if (input.size() == 3) {
                try{
                    
                    String title = input.get(0);
                    String file = input.get(1);
                    String description = input.get(2);
                    Integer creator_uid = sessionOpenedUser.get(Session);
  
                    String actionURL = generateActionUrl();
                    if(createCoursePermissions.contains(getRole(creator_uid))){
                        if(addAction(actionURL, "createCourse", input)){
                            if(emailSender.sendMail(getMailById(creator_uid), actionURL, "Create Course??")){
                                return 210;
                            }else{
                                return 701;
                            }
                        }
                        else{
                            return 205;
                        }
                    }else{
                        RateLimiterService.wrongEntry("/user/passwordCorrection", ip);
                        return 202;
                    }
                        
                    
                } catch (Exception e){
                    RateLimiterService.wrongEntry("/user/passwordCorrection", ip);
                    return 401;
                }
        
            } else {
                return 203;
            }
        }else{
            return 429;
        }
    }

    @PostMapping("/localad/create") 
    public static Integer createLocalAD(@RequestBody List<String> input, HttpServletRequest request) throws SQLException{
        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowedWOCount("/user/passwordCorrection", ip)){
            if (input.size() == 5) {
                try{
                    
                    String title = input.get(0);
                    String link = input.get(1);
                    String description = input.get(2);
                    Integer creator_uid = Integer.valueOf(input.get(3));
                    String creator_passwordH = hashPassword(input.get(4));
                    
                    
                    if(getPasswordById(creator_uid).equals(creator_passwordH)){
                        String actionURL = generateActionUrl();
                        if(createLocalADPermissions.contains(getRole(creator_uid))){
                            if(addAction(actionURL, "createLocalAD", input)){
                                if(emailSender.sendMail(getMailById(creator_uid), actionURL, "Create LocalAd??")){
                                    return 310;
                                }else{
                                    return 701;
                                }
                            }
                            else{
                                return 305;
                            }
                        }else{
                            return 302;
                        }
                        
                    }
                    else{
                        RateLimiterService.wrongEntry("/user/passwordCorrection", ip);
                        return 301;
                    }
                } catch (Exception e){
                    return 303;
                }
        
            } else {
                return 303;
            }
        }else{
            return 429;
        }
    }

    // DELETE Request → http://localhost:8080/user/delete
    @DeleteMapping("/user/delete")
    public Integer deleteUser(@RequestBody List<String> input, HttpServletRequest request) {
        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowedWOCount("/user/passwordCorrection", ip)){
            if (input.size() == 2) {
                Integer uid = Integer.valueOf(input.get(0));
                String passwordH = hashPassword(input.get(1));
                input.set(1, passwordH);
    
                if(getPasswordById(uid).equals(passwordH)){
    
                    String actionURL = generateActionUrl();
                    if(addAction(actionURL, "deleteUser", input)){
    
                        if(emailSender.sendMail(getMailById(uid), actionURL, "Delete User??")){
                            return 610;
                        }
                        return 701;
                    }
                    else{
                        return 605;
                    }                
                }else{
                    RateLimiterService.wrongEntry("/user/passwordCorrection", ip);
                    return 601;
                }
            }
            return 603;
        }else{
            return 429;
        }
    }
       
    // GET Request → http://localhost:8080/user/getUID
    @GetMapping("/user/getUid/{username}")
    public Integer getUID(@PathVariable String username, HttpServletRequest request) {
        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowed("/user/getUid", ip)){
            System.out.println(GREEN + "[INFO] " + getIpFromRequest(request) + " GOT UID OF " + username);
            Integer uid = getUIDFromUsername(username);

            if(uid == null){
                return 901;
            }
            return uid;
        }else{
            return 429;
        }
        
    }

    // GET Request → http://localhost:8080/user/getUIDbyMail
    @GetMapping("/user/getUIDByMail/{mail}")
    public Integer getUIDbyMail(@PathVariable String mail, HttpServletRequest request) {
        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowed("/user/getUid", ip)){
            System.out.println(GREEN + "[INFO] " + getIpFromRequest(request) + " GOT UID OF " + mail);
            Integer uid = getUIDFromMail(mail);

            if(uid == null){
                return 901;
            }
            return uid;
        }else{
            return 429;
        }
        
    }


    // GET Request → http://localhost:8080/question?exam=...&question=...
    @GetMapping("/question")
    public Map<String, Object> getQuestion(@RequestParam String exam, @RequestParam String question, HttpServletRequest request) {
        try{        
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("questions/" + exam + ".json");
            List<Map<String, Object>> jsonMap = mapper.readValue(resource.getInputStream(), List.class);
            Map<String, Object> questionObject = jsonMap.get(Integer.valueOf(question));
            questionObject.remove("answer");
            return questionObject;
        } catch(Exception e){
            return Map.of("error", e.getMessage());
        }
    }

    // GET Request → http://localhost:8080/exam?name=...
    @GetMapping("/exam")
    public List<Map<String, Object>> getQuestions(@RequestParam String name, HttpServletRequest request) {
        try{        
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("questions/" + name + ".json");
            List<Map<String, Object>> questionsBuffer = mapper.readValue(resource.getInputStream(), List.class);
            List<Map<String, Object>> questions = new ArrayList<>();
            for (Map<String,Object> question : questionsBuffer) {
                question.remove("answer");
                questions.add(question);
            }
            return questions;
        } catch(Exception e){
            return List.of(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/exam/submit")
    public Map<String, Object> submitExam(@RequestParam String examName, @RequestBody List<String> examResults, HttpServletRequest request, HttpSession session) {
        try{        
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("questions/" + examName + ".json");
            List<Map<String, Object>> questionsBuffer = mapper.readValue(resource.getInputStream(), List.class);
            Map<String, Object> answers = new HashMap<>();
            for (Map<String,Object> question : questionsBuffer) {
                answers.put("answer", String.valueOf(question.get("answer")));
            }

            Integer points = 0;
            Map<String, Object> returnMap = new HashMap<>();

            for (int i = 0; i < answers.size(); i++) {
                if(answers.get(i)==examResults.get(i)){
                    points+=1;
                    returnMap.put("question"+i, Map.of("res", true, "correctAnswer", answers.get(i)));
                }else{
                    returnMap.put("question"+i, Map.of("res", false, "correctAnswer", answers.get(i)));
                }
            }
            int uid = sessionOpenedUser.get(session);
            try {
                setUserPoints(uid, getUserPoints(uid) + points);
            } catch (Exception e) {
                return Map.of("points couldnt be updated", e);
            }
            setUserPoints(uid, getUserPoints(uid) + points);
            return returnMap;
        } catch(Exception e){
            of = Map.of("error", e.getMessage());
            return of;
        }
    }

    // POST Request → http://localhost:8080/user/login
    @PostMapping("/user/login")
    public static Integer isPasswordCorrect(@RequestBody List<String> input, HttpServletRequest request, HttpSession session) throws SQLException{
        String ip = getIpFromRequest(request);
        if(RateLimiterService.isAllowedWOCount("/user/passwordCorrection", ip)){
            if (input.size() == 2) {
                try{
                    Integer id = Integer.valueOf(input.get(0));
                    String passwordH = hashPassword(input.get(1));
                    input.set(1, passwordH);
                    
                    if(getPasswordById(Integer.valueOf(id)).equals(passwordH)){
                        sessionOpenedUser.put(session, id);
                        return 800;
                    }
                    else{
                        RateLimiterService.wrongEntry("/user/passwordCorrection", ip);
                        return 801;
                    }
                }catch(Exception e){
                    System.out.println(RED + e.getMessage() + RESET);
                    return 803;
                }
                
            }else{
                return 803;
            }
        }else{
            return 429;
        }
        
    }
    
    // PUT Request → http://localhost:8080/user/changePassword
    @PutMapping("/user/changePassword")
    public static Integer changePassword(@RequestBody List<String> input, HttpServletRequest request) throws SQLException{

        String ip = String.valueOf(getIpFromRequest(request));
        if(RateLimiterService.isAllowedWOCount("/user/passwordCorrection", ip)){
            if (input.size() == 2) {
                try{
                    Integer uid = Integer.valueOf(input.get(0));
                    String newPasswordH = hashPassword(input.get(1));
                    input.set(1, newPasswordH);
                    
                    String actionURL = generateActionUrl();
                    if(addAction(actionURL, "changeUserPassword", input)){

                        if(emailSender.sendMail(getMailById(uid), actionURL, "Change Password??")){
                            return 118;
                        }
                        return 701;
                    }
                    else{
                        return 105;
                    }  

                }catch(Exception e){
                    return 103;
                }
                
            }
            return 103;
        }else{
            return 429;
        }
    }


    @PutMapping("/")
    public String setUwU(@RequestBody String input) {
        return input;
    }
}