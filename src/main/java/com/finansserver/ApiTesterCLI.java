package com.finansserver;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ApiTesterCLI {
    private static final String BASE_URL = "http://localhost:8080";
    private static final Scanner scanner = new Scanner(System.in);
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Map<Integer, String> errorMap = new HashMap<>();

    private static Integer loggedInUID = null;
    private static String loggedInUsername = null;
    private static String loggedInPassword = null;

    public static void main(String[] args) {
        loadErrorCodes();
        
        while (true) {
            System.out.println("\n=== API Test CLI ===");
            System.out.println("1. Login");
            System.out.println("2. Logout");
            System.out.println("3. Create User");
            System.out.println("4. Get User UID");
            System.out.println("5. Create Course");
            System.out.println("6. Create LocalAD");
            System.out.println("7. Delete User");
            System.out.println("8. Change Password");
            System.out.println("9. Verify Action");
            System.out.println("0. Exit");
            System.out.print("Your choice: ");
            
            int choice = scanner.nextInt();
            scanner.nextLine();
            
            try {
                switch (choice) {
                    case 1 -> login();
                    case 2 -> logout();
                    case 3 -> createUser();
                    case 4 -> getUID();
                    case 5 -> createCourse();
                    case 6 -> createLocalAD();
                    case 7 -> deleteUser();
                    case 8 -> changePassword();
                    case 9 -> verifyAction();
                    case 0 -> {
                        System.out.println("Exiting...");
                        return;
                    }
                    default -> System.out.println("Invalid choice! Try again.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void login() throws Exception {
        System.out.println("\n--- Login ---");
        System.out.print("Username: ");
        loggedInUsername = scanner.nextLine();
        System.out.print("Password: ");
        loggedInPassword = scanner.nextLine();
        
        loggedInUID = sendGet("/user/getUid/" + loggedInUsername);
        
        Integer response = checkPassword(loggedInUID, loggedInPassword);

        if(response == 800) {
            System.out.println("Login successful!");
        } else {
            loggedInUID = null;
            loggedInUsername = null;
            loggedInPassword = null;
            System.out.println(getErrorMessage(response));
        }
    }

    private static void logout() {
        loggedInUID = null;
        loggedInUsername = null;
        loggedInPassword = null;
        System.out.println("Logged out.");
    }

    private static void createUser() throws Exception {
        System.out.println("\n--- Create User ---");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        System.out.print("Password: ");
        String password = scanner.nextLine();
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Occupation: ");
        String occupation = scanner.nextLine();
        
        String json = String.format("[\"%s\", \"%s\", \"%s\", \"%s\"]", 
                                    username, password, email, occupation);
        int response = sendPost("/user/create", json);
        System.out.println("Server Response: " + getErrorMessage(response));
    }

    private static void getUID() throws Exception {
        System.out.println("\n--- Get User UID ---");
        System.out.print("Username: ");
        String username = scanner.nextLine();
        
        int response = sendGet("/user/getUid/" + username);
        System.out.println("UID: " + getErrorMessage(response));
    }

    private static void createCourse() throws Exception {
        System.out.println("\n--- Create Course ---");
        System.out.print("Course Title: ");
        String title = scanner.nextLine();
        System.out.print("Course Link: ");
        String link = scanner.nextLine();
        System.out.print("Course Description: ");
        String description = scanner.nextLine();

        
        int creatorUid;
        String creatorPassword;
        if (loggedInUID != null) {
            creatorUid = loggedInUID;
            creatorPassword = loggedInPassword;
        } else {
            System.out.print("Creator UID: ");
            creatorUid = scanner.nextInt();
            scanner.nextLine();
            System.out.print("Password: ");
            creatorPassword = scanner.nextLine();
        }
        
        
        String json = String.format("[\"%s\", \"%s\", \"%s\", \"%d\", \"%s\"]", title, link, description, creatorUid, creatorPassword);
        int response = sendPost("/course/create", json);
        System.out.println("Server Response: " + getErrorMessage(response));
    }

    private static void createLocalAD() throws Exception {
        System.out.println("\n--- Create Course ---");
        System.out.print("Title: ");
        String title = scanner.nextLine();
        System.out.print("Link: ");
        String link = scanner.nextLine();
        System.out.print("Description: ");
        String description = scanner.nextLine();

        
        int creatorUid;
        String creatorPassword;
        if (loggedInUID != null) {
            creatorUid = loggedInUID;
            creatorPassword = loggedInPassword;
        } else {
            System.out.print("Creator UID: ");
            creatorUid = scanner.nextInt();
            scanner.nextLine();
            System.out.print("Password: ");
            creatorPassword = scanner.nextLine();
        }
        
        
        String json = String.format("[\"%s\", \"%s\", \"%s\", \"%d\", \"%s\"]", title, link, description, creatorUid, creatorPassword);
        int response = sendPost("/localad/create", json);
        System.out.println("Server Response: " + getErrorMessage(response));
    }

    private static void deleteUser() throws Exception {
        System.out.println("\n--- Delete User ---");

        int uid;
        String password;
        if (loggedInUID != null) {
            uid = loggedInUID;
            password = loggedInPassword;
        } else {
            System.out.print("UID: ");
            uid = scanner.nextInt();
            scanner.nextLine();
            System.out.print("Password: ");
            password = scanner.nextLine();
        }

        String json = String.format("[\"%d\", \"%s\"]", uid, password);
        int response = sendDelete("/user/delete", json);
        System.out.println("Server Response: " + getErrorMessage(response));
    }
    
    private static void changePassword() throws Exception {
        Integer UID = loggedInUID;
        
        if(loggedInUID == null) {
            System.out.print("UID: ");
            UID = scanner.nextInt();
            scanner.nextLine();
        }

        System.out.print("New Password: ");
        String newPassword = scanner.nextLine();
        
        String json = String.format("[\"%d\", \"%s\"]", UID, newPassword);
        int response = sendPUT("/user/changePassword", json);
        System.out.println("Server Response: " + getErrorMessage(response));
    }

    private static void verifyAction() throws Exception {
        System.out.println("\n--- Verify Action ---");
        System.out.print("Action URL: ");
        String actionUrl = scanner.nextLine();
        
        int response = sendGet("/action/" + actionUrl);
        System.out.println("Server Response: " + getErrorMessage(response));
    }

    private static int sendRequest(String method, String endpoint, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("Content-Type", "application/json");

        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
        } else if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(json));
        } else if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(json));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.DELETE();
            if (json != null) {
                builder.header("Content-Type", "application/json")
                       .method("DELETE", HttpRequest.BodyPublishers.ofString(json));
            }
        } else {
            throw new IllegalArgumentException("Invalid HTTP method: " + method);
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        try {
            return Integer.parseInt(response.body().trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid server response: " + response.body());
            return -1;
        }
    }

    private static int sendPost(String endpoint, String json) throws Exception {
        return sendRequest("POST", endpoint, json);
    }

    private static int sendGet(String endpoint) throws Exception {
        return sendRequest("GET", endpoint, null);
    }

    private static int sendDelete(String endpoint, String json) throws Exception {
        return sendRequest("DELETE", endpoint, json);
    }

    private static int sendPUT(String endpoint, String json) throws Exception {
        return sendRequest("PUT", endpoint, json);
    }

    private static String getErrorMessage(int code) {
        return errorMap.getOrDefault(code, "Unknown error code: " + code) + " (" + code + ")";
    }

    private static void loadErrorCodes() {
        String currentDir = Paths.get("").toAbsolutePath().toString();
        String filePath = currentDir + File.separator + "middlewareErrorCodesNew.txt";
        
        System.out.println("Loading error codes from: " + filePath);
        
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !line.startsWith("//")) continue;
                
                String content = line.substring(2).trim();
                String[] parts = content.split("->");
                if (parts.length == 2) {
                    try {
                        int code = Integer.parseInt(parts[0].trim());
                        String message = parts[1].trim();
                        errorMap.put(code, message);
                    } catch (NumberFormatException e) {
                        System.err.println("Skipping invalid line: " + line);
                    }
                }
            }
            System.out.println("Loaded " + errorMap.size() + " error codes");
        } catch (IOException e) {
            System.err.println("Error loading error codes: " + e.getMessage());
        }
    }

    private static int checkPassword(int userId, String password) throws Exception {
        String json = String.format("[\"%d\", \"%s\"]", userId, password);
        return sendPost("/user/passwordCorrection", json);
    }
}