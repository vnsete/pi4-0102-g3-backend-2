package br.servihub;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.Assert.*;

public class FinancialAccessTest {

    private HttpServer servidor;
    private HttpClient cliente;
    private String enderecoBase;

    @Before
    public void preparar() throws Exception {
        servidor = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0
        );

        Method rota = Main.class.getDeclaredMethod(
            "route",
            com.sun.net.httpserver.HttpExchange.class
        );
        rota.setAccessible(true);

        servidor.createContext("/", exchange -> {
            try {
                rota.invoke(null, exchange);
            } catch (Exception erro) {
                exchange.close();
            }
        });

        servidor.start();

        enderecoBase = "http://127.0.0.1:"
            + servidor.getAddress().getPort();

        cliente = HttpClient.newHttpClient();
    }

    @After
    public void finalizar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    private HttpResponse<String> requisitar(
        String caminho,
        String metodo
    ) throws Exception {

        HttpRequest.Builder requisicao = HttpRequest.newBuilder()
            .uri(URI.create(enderecoBase + caminho));

        if ("POST".equals(metodo)) {
            requisicao.POST(
                HttpRequest.BodyPublishers.noBody()
            );
        } else if ("PUT".equals(metodo)) {
            requisicao.PUT(
                HttpRequest.BodyPublishers.noBody()
            );
        } else {
            requisicao.GET();
        }

        return cliente.send(
            requisicao.build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    // Verifica se uma rota inexistente retorna erro 404.
    @Test
    public void deveRejeitarRotaInexistente() throws Exception {
        HttpResponse<String> resposta = requisitar(
            "/rota-inexistente", "GET"
        );

        assertEquals(404, resposta.statusCode());
        assertTrue(
            resposta.body().contains("ROTA_NAO_ENCONTRADA")
        );
    }

    // Impede requisições POST na consulta de lembretes.
    @Test
    public void deveBloquearPostEmLembretes() throws Exception {
        HttpResponse<String> resposta = requisitar(
            "/lembretes", "POST"
        );

        assertEquals(405, resposta.statusCode());
        assertTrue(
            resposta.body().contains("METODO_NAO_PERMITIDO")
        );
    }

    // Impede alterações por PUT na consulta de pendências.
    @Test
    public void deveBloquearPutEmPendencias() throws Exception {
        HttpResponse<String> resposta = requisitar(
            "/pendencias", "PUT"
        );

        assertEquals(405, resposta.statusCode());
        assertTrue(
            resposta.body().contains("METODO_NAO_PERMITIDO")
        );
    }

    // Verifica se o erro informa o método HTTP permitido.
    @Test
    public void deveInformarMetodoPermitido() throws Exception {
        HttpResponse<String> resposta = requisitar(
            "/lembretes", "POST"
        );

        assertEquals(405, resposta.statusCode());
        assertEquals(
            "GET",
            resposta.headers()
                .firstValue("Allow")
                .orElse("")
        );
    }
}

