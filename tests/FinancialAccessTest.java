package br.servihub;
import static br.servihub.Common.*;
import static org.junit.Assert.*;
import org.junit.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class FinancialAccessTest {
 @Test public void onlyAdministratorPassesFinancialAuthorization()throws Exception{
  Assume.assumeTrue("Use BACKEND1_URL=http://127.0.0.1:19081 for the mock",env("BACKEND1_URL","").equals("http://127.0.0.1:19081"));
  AtomicReference<String> role=new AtomicReference<>("cliente");
  HttpServer management=HttpServer.create(new InetSocketAddress("127.0.0.1",19081),0);
  management.createContext("/interno/sessao",x->{try{
   if(!KEY.equals(x.getRequestHeaders().getFirst("X-ServiHUB-Internal"))){send(x,403,Map.of("codigo","CHAVE_INVALIDA"));return;}
   send(x,200,Map.of("autenticado",true,"papel",role.get()));
  }finally{x.close();}});
  HttpServer finance=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  finance.createContext("/",x->{try{Backend2.handle(x);}catch(Failure e){send(x,e.status,Map.of("codigo",e.code));}catch(Exception e){send(x,500,Map.of("codigo","ERRO_TESTE"));}finally{x.close();}});
  management.start();finance.start();
  try{
   HttpClient client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+finance.getAddress().getPort();
   for(String currentRole:new String[]{"cliente","funcionario"}){
    role.set(currentRole);
    for(String route:new String[]{"/pagamentos","/orcamentos","/pendencias","/lembretes"}){
     assertEquals(currentRole+route,403,client.send(HttpRequest.newBuilder(URI.create(base+route)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
   }
   role.set("admin");
   assertEquals(404,client.send(HttpRequest.newBuilder(URI.create(base+"/rota-de-teste-inexistente")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
  }finally{finance.stop(0);management.stop(0);}
 }
}
