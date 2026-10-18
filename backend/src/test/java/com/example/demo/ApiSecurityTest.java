package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/** Real HTTP against a random port: authentication, roles, and the readable error bodies. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "EV_DB_TEST", matches = "true")
class ApiSecurityTest {

    @Value("${local.server.port}") int port;
    @Value("${seed.admin-password:}") String adminPassword;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> call(String method, String path, String body, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookieOf(HttpResponse<String> r) {
        return r.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("access_token="))
                .map(c -> c.split(";")[0]).findFirst().orElseThrow();
    }

    @Test
    void anonymousUsersAreRejected() throws Exception {
        assertEquals(403, call("GET", "/api/bookings/me", null, null).statusCode());
        assertEquals(403, call("POST", "/api/bookings", "{\"slotId\":1,\"durationMinutes\":60}", null).statusCode());
        assertEquals(403, call("GET", "/api/wallet", null, null).statusCode());
    }

    @Test
    void normalUserCannotUseAdminEndpointsAndGetsReadableErrors() throws Exception {
        String name = "t_" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> signup = call("POST", "/api/auth/signup",
                "{\"name\":\"T\",\"username\":\"" + name + "\",\"email\":\"" + name + "@t.local\",\"password\":\"password123\"}", null);
        assertEquals(201, signup.statusCode());
        String cookie = cookieOf(signup);

        assertTrue(call("GET", "/api/users/me", null, cookie).body().contains("\"role\":\"USER\""));
        assertEquals("0.00", call("GET", "/api/wallet", null, cookie).body().replaceAll("\\D*(\\d+\\.\\d+).*", "$1"),
                "signup must create the wallet");

        for (String[] r : new String[][] {{"GET", "/api/admin/bookings"}, {"GET", "/api/admin/revenue"},
                {"GET", "/api/admin/gateway-log"}}) {
            assertEquals(403, call(r[0], r[1], null, cookie).statusCode(), r[1]);
        }
        assertEquals(403, call("POST", "/api/admin/slots/1/maintenance", "{\"enabled\":true}", cookie).statusCode());
        assertEquals(403, call("POST", "/api/bills/1/settle", "{\"energyKwh\":1}", cookie).statusCode());

        // booking without holding the slot -> 409 with the reason in the body
        HttpResponse<String> notHeld = call("POST", "/api/bookings", "{\"slotId\":1,\"durationMinutes\":60}", cookie);
        assertEquals(409, notHeld.statusCode());
        assertTrue(notHeld.body().contains("do not hold"), notHeld.body());
        // invalid duration is rejected by validation
        assertEquals(400, call("POST", "/api/bookings", "{\"slotId\":1,\"durationMinutes\":5}", cookie).statusCode());
    }

    @Test
    void adminCanReadRevenueAndToggleMaintenance() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(adminPassword.isBlank(), "seed.admin-password not set");
        HttpResponse<String> login = call("POST", "/api/auth/login",
                "{\"username\":\"admin\",\"password\":\"" + adminPassword + "\"}", null);
        assertEquals(200, login.statusCode());
        String cookie = cookieOf(login);

        assertEquals(200, call("GET", "/api/admin/revenue?period=daily", null, cookie).statusCode());
        assertEquals(400, call("GET", "/api/admin/revenue?period=bogus", null, cookie).statusCode());
        assertEquals(200, call("GET", "/api/admin/bookings", null, cookie).statusCode());
        assertEquals(404, call("POST", "/api/admin/slots/999999/maintenance", "{\"enabled\":true}", cookie).statusCode());
    }
}
