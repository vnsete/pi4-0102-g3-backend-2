package br.servihub;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();
  private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

  public static void main(String[] args) throws IOException {
    HttpServer server = HttpServer.create(
      new InetSocketAddress(Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"))),
      0
    );
    server.createContext("/", Main::route);
    server.start();
  }

  private static void route(HttpExchange exchange) throws IOException {
    try {
      String path = exchange.getRequestURI().getPath();
      if (!path.equals("/lembretes") && !path.equals("/pendencias")) {
        reply(exchange, 404, "{\"erro\":\"ROTA_NAO_ENCONTRADA\"}");
        return;
      }
      if (!exchange.getRequestMethod().equals("GET")) {
        exchange.getResponseHeaders().set("Allow", "GET");
        reply(exchange, 405, "{\"erro\":\"METODO_NAO_PERMITIDO\"}");
        return;
      }
      String base = System.getenv().getOrDefault("BACKEND1_URL", "http://localhost:8080");
      String cookie = exchange.getRequestHeaders().getFirst("Cookie");
      HttpRequest sessionRequest = HttpRequest.newBuilder(URI.create(base + "/sessao"))
        .timeout(Duration.ofSeconds(10))
        .header("Cookie", cookie == null ? "" : cookie)
        .GET()
        .build();
      HttpResponse<String> session = CLIENT.send(
        sessionRequest,
        HttpResponse.BodyHandlers.ofString()
      );
      if (session.statusCode() != 200) {
        reply(exchange, 502, "{\"erro\":\"INTEGRACAO_INDISPONIVEL\"}");
        return;
      }
      if (!JSON.readTree(session.body()).path("autenticado").asBoolean()) {
        reply(exchange, 401, "{\"erro\":\"SESSAO_INVALIDA\"}");
        return;
      }
      String target = base + (path.equals("/lembretes") ? "/interno/visitas" : "/pendencias");
      HttpRequest request = HttpRequest.newBuilder(URI.create(target))
        .timeout(Duration.ofSeconds(10))
        .header("X-ServiHUB-Internal", System.getenv("INTERNAL_API_KEY"))
        .GET()
        .build();
      HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200 || !path.equals("/lembretes")) {
        reply(exchange, response.statusCode(), response.body());
        return;
      }
      List<Map<String, Object>> visits = JSON.readValue(response.body(), new TypeReference<>() {});
      List<Map<String, Object>> next = new ArrayList<>();
      LocalDateTime now = LocalDateTime.now(ZONE);
      for (Map<String, Object> visit : visits) {
        LocalDateTime when = LocalDateTime.parse(visit.get("inicio").toString().replace(' ', 'T'));
        if (!when.isBefore(now) && when.isBefore(now.plusHours(24))) {
          next.add(visit);
        }
      }
      reply(exchange, 200, JSON.writeValueAsString(next));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      reply(exchange, 502, "{\"erro\":\"INTEGRACAO_INDISPONIVEL\"}");
    } catch (Exception exception) {
      reply(exchange, 502, "{\"erro\":\"INTEGRACAO_INDISPONIVEL\"}");
    }
  }

  private static void reply(HttpExchange exchange, int status, String data) throws IOException {
    byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    } finally {
      exchange.close();
    }
  }
}
