package br.servihub;
import static br.servihub.Common.*;
import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.math.BigDecimal;

public final class Backend2 {
    private static final String B1=env("BACKEND1_URL","http://127.0.0.1:8081");
    public static void main(String[]args)throws Exception {server(Integer.parseInt(env("PORT","8082")),Backend2::handle);}
    private static boolean method(HttpExchange x,String m){return x.getRequestMethod().equals(m);}
    private static JsonObject remote(HttpExchange x,String route)throws Exception {
        try {
            HttpRequest r=HttpRequest.newBuilder(URI.create(B1+route)).timeout(Duration.ofSeconds(5)).header("Cookie",cookieHeader(x)).header("X-ServiHUB-Internal",KEY).GET().build();
            HttpResponse<String> response=HTTP.send(r,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()==401)throw fail(401,"SESSAO_INVALIDA","Entre novamente para continuar.");
            require(response.statusCode()==200,503,"GESTAO_INDISPONIVEL","O módulo de gestão está indisponível.");
            return JSON.fromJson(response.body(),JsonObject.class);
        }catch(Failure e){throw e;}catch(Exception e){throw fail(503,"GESTAO_INDISPONIVEL","Não foi possível consultar o módulo de gestão.");}
    }
    public static void handle(HttpExchange x)throws Exception {
        String path=x.getRequestURI().getPath();JsonObject session=remote(x,"/interno/sessao");
        require(session!=null&&session.has("autenticado")&&session.get("autenticado").getAsBoolean(),503,"GESTAO_INDISPONIVEL","Resposta de sessão inválida.");
        require(session.has("papel")&&"admin".equals(session.get("papel").getAsString()),403,"ACESSO_NEGADO","Acesso reservado ao administrador.");
        if(!method(x,"GET"))origin(x);
        String[] p=path.split("/");
        if(p.length==4&&p[1].equals("orcamentos")&&p[3].equals("status")&&method(x,"PATCH")){
            int id=id(p[2]);JsonObject j=body(x);fields(j,"status");String status=text(j,"status",10,false);bad(Set.of("aprovado","recusado").contains(status),"Escolha aprovado ou recusado.");
            send(x,200,tx(c->{Map<String,Object> o=one(c,"SELECT * FROM orcamento WHERE id=? FOR UPDATE",id);require(o!=null,404,"ORCAMENTO_NAO_ENCONTRADO","Orçamento não encontrado.");require("pendente".equals(o.get("status")),409,"TRANSICAO_INVALIDA","Este orçamento já foi decidido.");Map<String,Object> updated=one(c,"UPDATE orcamento SET status=?,atualizado_em=CURRENT_TIMESTAMP(6) WHERE id=? RETURNING "+ORCAMENTO,status,id);if(status.equals("aprovado")){update(c,"INSERT INTO pagamento_orcamento(orcamento_id,valor,situacao) VALUES(?,?,'pendente')",id,o.get("valor_total"));}return updated;}));return;
        }
        if(p.length==3&&p[1].equals("pagamentos-orcamento")&&method(x,"PATCH")){
            int payment=id(p[2]);JsonObject j=body(x);fields(j,"situacao");String status=text(j,"situacao",10,false);
            require(Set.of("pago","pendente").contains(status),400,"STATUS_INVALIDO","Situação inválida.");
            send(x,200,tx(c->{Map<String,Object> old=one(c,"SELECT id FROM pagamento_orcamento WHERE id=? FOR UPDATE",payment);require(old!=null,404,"PAGAMENTO_NAO_ENCONTRADO","Pagamento não encontrado.");return one(c,"UPDATE pagamento_orcamento SET situacao=?,pago_em=CASE WHEN ?='pago' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,atualizado_em=CURRENT_TIMESTAMP(6) WHERE id=? RETURNING id AS \"pagamentoId\",situacao,valor",status,status,payment);}));return;
        }
        if(path.equals("/pagamentos")&&method(x,"GET")){query(x);try(Connection c=db()){send(x,200,Map.of("pagamentos",rows(c,"SELECT p.id AS \"pagamentoId\",p.servico_id AS \"servicoId\",s.cliente_id AS \"clienteId\",c.nome AS \"nomeCliente\",s.descricao,p.valor,p.situacao,p.vencimento_em AS \"vencimentoEm\",p.pago_em AS \"pagoEm\",p.atualizado_em AS \"atualizadoEm\" FROM pagamento p JOIN servico s ON s.id=p.servico_id JOIN cliente c ON c.id=s.cliente_id ORDER BY p.id DESC"),"pagamentosOrcamento",rows(c,"SELECT po.id AS \"pagamentoId\",NULL AS \"servicoId\",o.cliente_id AS \"clienteId\",cl.nome AS \"nomeCliente\",o.descricao,po.valor,po.situacao,po.vencimento_em AS \"vencimentoEm\",po.pago_em AS \"pagoEm\",po.atualizado_em AS \"atualizadoEm\" FROM pagamento_orcamento po JOIN orcamento o ON o.id=po.orcamento_id JOIN cliente cl ON cl.id=o.cliente_id ORDER BY po.id DESC")));}return;}
        if(path.equals("/orcamentos")&&method(x,"POST")){
            JsonObject j=body(x);fields(j,"clienteId","catalogoId","descricao","valorTotal");int cliente=numberId(j,"clienteId",false);int catalogo=numberId(j,"catalogoId",false);String desc=text(j,"descricao",500,false);BigDecimal valor=money(j,"valorTotal",false);
            send(x,201,tx(c->{found(c,"cliente",cliente,"CLIENTE_NAO_ENCONTRADO");found(c,"catalogo_servico",catalogo,"CATALOGO_NAO_ENCONTRADO");return one(c,"INSERT INTO orcamento(cliente_id,catalogo_id,descricao,valor_total) VALUES(?,?,?,?) RETURNING "+ORCAMENTO,cliente,catalogo,desc,valor);}));return;
        }
        if(p.length==4&&p[1].equals("clientes")&&p[3].equals("orcamentos")&&method(x,"GET")){
            query(x);int cliente=id(p[2]);try(Connection c=db()){found(c,"cliente",cliente,"CLIENTE_NAO_ENCONTRADO");send(x,200,Map.of("orcamentos",rows(c,"SELECT "+ORCAMENTO+" FROM orcamento WHERE cliente_id=? ORDER BY criado_em DESC,id DESC",cliente)));}return;
        }
        if(path.equals("/orcamentos")&&method(x,"GET")){query(x);try(Connection c=db()){send(x,200,Map.of("orcamentos",rows(c,"SELECT "+ORCAMENTO+" FROM orcamento ORDER BY criado_em DESC,id DESC")));}return;}
        if(p.length==4&&p[1].equals("servicos")&&p[3].equals("pagamento")){
            int servico=id(p[2]);
            if(method(x,"GET")){query(x);try(Connection c=db()){found(c,"servico",servico,"SERVICO_NAO_ENCONTRADO");Map<String,Object> r=one(c,"SELECT "+PAGAMENTO+" FROM pagamento WHERE servico_id=?",servico);require(r!=null,404,"PAGAMENTO_NAO_ENCONTRADO","Ainda não há pagamento registrado para o serviço.");send(x,200,r);}return;}
            if(method(x,"PUT")){
                JsonObject j=body(x);fields(j,"valor","situacao","vencimentoEm");String vencimento=text(j,"vencimentoEm",10,true);if(vencimento!=null){try{bad(LocalDate.parse(vencimento).toString().equals(vencimento),"Vencimento inválido.");}catch(java.time.format.DateTimeParseException e){throw fail(400,"DADOS_INVALIDOS","Vencimento inválido.");}}final String due=vencimento;BigDecimal valor=money(j,"valor",false);String status=text(j,"situacao",10,false);bad(Set.of("pendente","pago").contains(status),"Situação deve ser pendente ou pago.");
                send(x,200,tx(c->{Map<String,Object> s=one(c,"SELECT id FROM servico WHERE id=? FOR UPDATE",servico);require(s!=null,404,"SERVICO_NAO_ENCONTRADO","Serviço não encontrado.");
                    return one(c,"INSERT INTO pagamento(servico_id,valor,situacao,pago_em,vencimento_em) VALUES(?,?,?,CASE WHEN ?='pago' THEN CURRENT_TIMESTAMP(6) ELSE NULL END,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id),valor=VALUES(valor),pago_em=CASE WHEN VALUES(situacao)='pendente' THEN NULL WHEN situacao='pago' THEN pago_em ELSE CURRENT_TIMESTAMP(6) END,situacao=VALUES(situacao),vencimento_em=VALUES(vencimento_em),atualizado_em=CURRENT_TIMESTAMP(6) RETURNING "+PAGAMENTO,servico,valor,status,status,due);}));return;
            }
        }
        if(path.equals("/pendencias")&&method(x,"GET")){
            query(x);String sql="SELECT p.id AS \"pagamentoId\",s.id AS \"servicoId\",s.cliente_id AS \"clienteId\",c.nome AS \"nomeCliente\",s.descricao,p.valor,p.situacao,p.atualizado_em AS \"atualizadoEm\" FROM pagamento p JOIN servico s ON s.id=p.servico_id JOIN cliente c ON c.id=s.cliente_id WHERE p.situacao='pendente' ORDER BY p.atualizado_em,p.id";
            try(Connection c=db()){send(x,200,Map.of("pendencias",rows(c,sql)));}return;
        }
        if(path.equals("/lembretes")&&method(x,"GET")){
            query(x);Instant now=Instant.now();String query="?de="+URLEncoder.encode(now.toString(),StandardCharsets.UTF_8)+"&ate="+URLEncoder.encode(now.plusSeconds(86400).toString(),StandardCharsets.UTF_8);
            JsonObject data=remote(x,"/interno/visitas"+query);List<Map<String,Object>> list=new ArrayList<>();
            try {
                for(JsonElement el:data.getAsJsonArray("atendimentos")){
                    JsonObject a=el.getAsJsonObject();Map<String,Object> m=new LinkedHashMap<>();
                    for(String k:List.of("agendamentoId","servicoId","clienteId"))m.put(k,numberId(a,k,false));
                    String nome=text(a,"nomeCliente",120,false),date=text(a,"inicioEm",50,false);
                    m.put("nomeCliente",nome);m.put("inicioEm",date);
                    m.put("mensagem","Visita a "+nome+" em "+DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo")).format(instant(date))+".");list.add(m);
                }
            }catch(Exception e){throw fail(503,"GESTAO_INDISPONIVEL","Resposta de visitas inválida.");}
            send(x,200,Map.of("lembretes",list));return;
        }
        throw fail(404,"ROTA_NAO_ENCONTRADA","Operação não encontrada.");
    }
}
