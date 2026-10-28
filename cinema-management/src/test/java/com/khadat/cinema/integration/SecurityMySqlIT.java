package com.khadat.cinema.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses real MySQL, Tomcat, session cookies and tokens from /csrf. No mock user/csrf helpers. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="CINEMA_MYSQL_IT",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(SecurityMySqlIT.ProbeConfiguration.class)
class SecurityMySqlIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired AdminProbeController probe;
    @LocalServerPort int port;
    private final String suffix=UUID.randomUUID().toString();
    private final String adminEmail="admin-"+suffix+"@example.invalid";
    private final String staffEmail="staff-"+suffix+"@example.invalid";
    private final String disabledEmail="disabled-"+suffix+"@example.invalid";
    private final String adminPassword=secret();
    private final String staffPassword=secret();
    private final String disabledPassword=secret();

    private static String secret() {
        byte[] bytes=new byte[24];new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @BeforeAll void accounts() {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).startsWith("cinema_management_test_");
        create(adminEmail,adminPassword,"ADMIN","ACTIVE");
        create(staffEmail,staffPassword,"STAFF","ACTIVE");
        create(disabledEmail,disabledPassword,"STAFF","INACTIVE");
    }

    private void create(String email,String password,String role,String status) {
        jdbc.update("INSERT INTO users(username,email,display_name,password_hash,status) VALUES(?,?,?,?,?)",
            email.substring(0,email.indexOf('@')),email,"Security test fixture",encoder.encode(password),status);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON r.code=? WHERE u.email=?",role,email);
    }

    @AfterAll void removeOwnFixturesOnly() {
        for(String email:new String[]{adminEmail,staffEmail,disabledEmail}) {
            jdbc.update("DELETE link FROM user_roles link JOIN users u ON link.user_id=u.id WHERE u.email=?",email);
            jdbc.update("DELETE FROM users WHERE email=?",email);
        }
    }

    private final class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> send(String method,String path,String body,String token) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Accept","application/json");
            if(token!=null)builder.header("X-CSRF-TOKEN",token);
            if(body!=null)builder.header("Content-Type","application/json");
            return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        String csrf() throws Exception {
            var response=send("GET","/api/auth/csrf",null,null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(json.readTree(response.body()).get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
            return json.readTree(response.body()).get("token").asText();
        }
        HttpResponse<String> login(String email,String password,String token) throws Exception {
            return send("POST","/api/auth/login",json.writeValueAsString(Map.of("email",email,"password",password)),token);
        }
        String sessionId() { return cookies.getCookieStore().getCookies().stream()
            .filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
    }

    private Browser signedIn(boolean admin) throws Exception {
        Browser browser=new Browser();
        assertThat(browser.login(admin?adminEmail:staffEmail,admin?adminPassword:staffPassword,browser.csrf()).statusCode()).isEqualTo(200);
        return browser;
    }

    private void error(HttpResponse<String> response,int status,String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        JsonNode body=json.readTree(response.body());
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.has("timestamp")&&body.has("path")&&body.has("message")&&body.get("fieldErrors").isObject()).isTrue();
        assertThat(response.headers().firstValue("location")).isEmpty();
        assertThat(body.has("trace")||body.has("exception")).isFalse();
    }

    @Test void cookieAndSessionFixationProtection() throws Exception {
        Browser b=new Browser();var response=b.send("GET","/api/auth/csrf",null,null);
        String cookie=response.headers().firstValue("set-cookie").orElseThrow().toLowerCase();
        assertThat(cookie).contains("httponly","samesite=lax").doesNotContain("; secure");
        String before=b.sessionId();String csrf=json.readTree(response.body()).get("token").asText();
        assertThat(b.login(adminEmail,adminPassword,csrf).statusCode()).isEqualTo(200);
        assertThat(b.sessionId()).isNotEqualTo(before);
        HttpResponse<String> old=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me"))
            .header("Cookie","JSESSIONID="+before).GET().build(),HttpResponse.BodyHandlers.ofString());
        error(old,401,"UNAUTHENTICATED");
    }

    @Test void loginAndMeReturnOnlySafeIdentity() throws Exception {
        Browser b=signedIn(false);var response=b.send("GET","/api/auth/me",null,null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode user=json.readTree(response.body());
        assertThat(user.get("email").asText()).isEqualTo(staffEmail);
        assertThat(user.get("roles").get(0).asText()).isEqualTo("STAFF");
        assertThat(user.size()).isEqualTo(5);
        assertThat(user.has("password")||user.has("passwordHash")||response.body().contains(staffPassword)).as("No credentials in response").isFalse();
    }

    @Test void wrongUnknownAndInactiveLoginHaveSameFailure() throws Exception {
        for(String email:new String[]{adminEmail,"missing-"+suffix+"@example.invalid",disabledEmail}) {
            Browser b=new Browser();String password=email.equals(disabledEmail)?disabledPassword:secret();
            var response=b.login(email,password,b.csrf());error(response,401,"INVALID_CREDENTIALS");
            assertThat(json.readTree(response.body()).get("message").asText()).isEqualTo("Thông tin đăng nhập không hợp lệ.");
            assertThat(response.body().contains(password)||response.body().contains("$2a$")||response.body().contains("$2b$")).isFalse();
        }
    }

    @Test void anonymousMeAndProtectedReadAre401() throws Exception {
        Browser b=new Browser();error(b.send("GET","/api/auth/me",null,null),401,"UNAUTHENTICATED");
        error(b.send("GET","/api/admin/users",null,null),401,"UNAUTHENTICATED");
    }

    @Test void staffWithValidSessionAndCsrfCannotWriteAdminResource() throws Exception {
        Browser b=signedIn(false);int before=probe.writes.get();
        error(b.send("POST","/api/admin/users","{}",b.csrf()),403,"FORBIDDEN");
        assertThat(probe.writes.get()).isEqualTo(before);
    }

    @Test void adminWithValidCsrfReachesTestOnlyAdminController() throws Exception {
        Browser b=signedIn(true);int before=probe.writes.get();
        assertThat(b.send("POST","/api/admin/users","{}",b.csrf()).statusCode()).isEqualTo(200);
        assertThat(probe.writes.get()).isEqualTo(before+1);
    }

    @Test void staffCanReachOnlyReservedShowtimeAndBookingActions() throws Exception {
        Browser b=signedIn(false);
        assertThat(b.send("GET","/api/showtimes",null,null).statusCode()).isEqualTo(200);
        error(b.send("POST","/api/bookings","{}",b.csrf()),400,"VALIDATION_FAILED");
        error(b.send("POST","/api/bookings/1/cancel","{}",b.csrf()),403,"FORBIDDEN");
        error(b.send("GET","/api/admin/users-export",null,null),403,"FORBIDDEN");
    }

    @Test void csrfRequiredForLoginAndInvalidTokenRejected() throws Exception {
        Browser b=new Browser();b.csrf();
        error(b.login(adminEmail,adminPassword,null),403,"CSRF_INVALID");
        error(b.login(adminEmail,adminPassword,"invalid"),403,"CSRF_INVALID");
    }

    @Test void csrfRequiredEvenForAdminMutation() throws Exception {
        Browser b=signedIn(true);int before=probe.writes.get();
        error(b.send("POST","/api/admin/users","{}",null),403,"CSRF_INVALID");
        error(b.send("POST","/api/admin/users","{}","invalid"),403,"CSRF_INVALID");
        assertThat(probe.writes.get()).isEqualTo(before);
    }

    @Test void tokenBeforeLoginNoLongerWorksAfterLogin() throws Exception {
        Browser b=new Browser();String old=b.csrf();assertThat(b.login(adminEmail,adminPassword,old).statusCode()).isEqualTo(200);
        error(b.send("POST","/api/admin/users","{}",old),403,"CSRF_INVALID");
        assertThat(b.send("POST","/api/admin/users","{}",b.csrf()).statusCode()).isEqualTo(200);
    }

    @Test void logoutRequiresCsrfAndOldSessionCannotBeReused() throws Exception {
        Browser b=signedIn(false);String oldSession=b.sessionId();
        error(b.send("POST","/api/auth/logout",null,null),403,"CSRF_INVALID");
        assertThat(b.send("GET","/api/auth/me",null,null).statusCode()).isEqualTo(200);
        var logout=b.send("POST","/api/auth/logout",null,b.csrf());assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.headers().allValues("set-cookie").toString().toLowerCase()).contains("max-age=0");
        HttpResponse<String> replay=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/me"))
            .header("Cookie","JSESSIONID="+oldSession).GET().build(),HttpResponse.BodyHandlers.ofString());
        error(replay,401,"UNAUTHENTICATED");error(b.send("GET","/api/auth/me",null,null),401,"UNAUTHENTICATED");
        assertThat(b.login(staffEmail,staffPassword,b.csrf()).statusCode()).isEqualTo(200);
    }

    @Test void getLogoutDoesNotLogOut() throws Exception {
        Browser b=signedIn(false);error(b.send("GET","/api/auth/logout",null,null),403,"FORBIDDEN");
        assertThat(b.send("GET","/api/auth/me",null,null).statusCode()).isEqualTo(200);
    }

    @Test void malformedOrMissingLoginFieldsAre400WithValidCsrf() throws Exception {
        Browser b=new Browser();String token=b.csrf();
        error(b.send("POST","/api/auth/login","{broken",token),400,"INVALID_REQUEST");
        var missing=b.send("POST","/api/auth/login","{}",token);error(missing,400,"VALIDATION_FAILED");
        assertThat(json.readTree(missing.body()).get("fieldErrors").has("password")).isTrue();
    }

    @Test void databaseContainsBcryptInsteadOfPlaintext() {
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,adminEmail);
        assertThat(hash!=null&&hash.startsWith("$2")&&encoder.matches(adminPassword,hash)&&!hash.equals(adminPassword)).isTrue();
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class ProbeConfiguration {
        @Bean AdminProbeController adminProbeController() { return new AdminProbeController(); }
    }
    @RestController
    static class AdminProbeController {
        final AtomicInteger writes=new AtomicInteger();
        @PostMapping("/api/admin/users") Map<String,Boolean> write() { writes.incrementAndGet();return Map.of("testOnly",true); }
        @GetMapping("/api/admin/users") Map<String,Boolean> read() { return Map.of("testOnly",true); }
    }
}
