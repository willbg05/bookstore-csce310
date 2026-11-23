package auth;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public final class AuthServer {
    public static void start(AuthHandler handler) throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 8081), 0
        );

        server.createContext("/auth/login", handler::login);
        server.createContext("/auth/session", handler::session);
        server.createContext("/auth/logout", handler::logout);

        server.start();
    }
}
