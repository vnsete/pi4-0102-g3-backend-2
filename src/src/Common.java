package br.servihub;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.math.BigDecimal;

/** Shared HTTP/JDBC conventions. No database connection or transaction is shared by requests. */
public final class Common {
    public static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setStrictness(Strictness.STRICT).create();
    public static final String ORIGIN = env("PUBLIC_ORIGIN", "http://localhost:8080");
    public static final String KEY = env("INTERNAL_API_KEY", "");
    public static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public static String env(String key, String fallback) { String v=System.getenv(key); return v==null?fallback:v; }
    public static final class Failure extends RuntimeException {
        private static final long serialVersionUID=1L;
        public final int status; public final String code;
        public Failure(int s,String c,String m) {super(m);status=s;code=c;}
    }
    public static Failure fail(int s,String c,String m){return new Failure(s,c,m);}
    public static void require(boolean ok,int s,String code,String message){if(!ok)throw fail(s,code,message);}
    public static void bad(boolean ok,String message){require(ok,400,"DADOS_INVALIDOS",message);}
    public interface Handler { void handle(HttpExchange x) throws Exception; }
    public static void server(int port,Handler handler) throws IOException {
        require(KEY.length()>=32,503,"CONFIGURACAO_INVALIDA","Configure INTERNAL_API_KEY com ao menos 32 caracteres.");
        HttpServer s=HttpServer.create(new InetSocketAddress(env("BIND_HOST","127.0.0.1"),port),64);
        s.createContext("/",x->{
            try {
                x.getResponseHeaders().set("Cache-Control","no-store");
                if(x.getRequestURI().getPath().equals("/health")) {try(Connection c=db()){send(x,200,Map.of("status","ok"));}return;}
                handler.handle(x);
            } catch(Failure e){send(x,e.status,Map.of("codigo",e.code,"mensagem",e.getMessage()));}
            catch(SQLException e){
                String state=e.getSQLState();
                if((state!=null&&state.startsWith("23"))||e.getErrorCode()==3819)send(x,409,Map.of("codigo","CONFLITO_DADOS","mensagem","O vínculo ou registro conflita com os dados atuais."));
                else {System.err.println("Falha JDBC: "+state);send(x,503,Map.of("codigo","BANCO_INDISPONIVEL","mensagem","Não foi possível acessar os dados. Tente consultar novamente."));}
            } catch(Exception e){System.err.println("Falha: "+e.getClass().getSimpleName());send(x,500,Map.of("codigo","ERRO_INTERNO","mensagem","Não foi possível concluir a operação."));}
            finally{x.close();}
        });
        s.setExecutor(Executors.newFixedThreadPool(12));s.start();
        System.out.println("ServiHUB ouvindo em "+port);
    }
    public static void send(HttpExchange x,int status,Object value)throws IOException {
        byte[] bytes=JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);
    }
    public static JsonObject body(HttpExchange x)throws IOException {
        require(Objects.toString(x.getRequestHeaders().getFirst("Content-Type"),"").split(";",2)[0].trim().equalsIgnoreCase("application/json"),415,"TIPO_DE_CONTEUDO_INVALIDO","Envie application/json.");
        byte[] bytes=x.getRequestBody().readNBytes(65537);require(bytes.length<=65536,413,"CORPO_MUITO_GRANDE","O limite é 64 KiB.");
        try{
            com.google.gson.stream.JsonReader reader=new com.google.gson.stream.JsonReader(new StringReader(new String(bytes,StandardCharsets.UTF_8)));
            reader.setStrictness(Strictness.STRICT);bad(reader.peek()==com.google.gson.stream.JsonToken.BEGIN_OBJECT,"Envie um objeto JSON.");reader.beginObject();JsonObject j=new JsonObject();
            while(reader.hasNext()){String name=reader.nextName();bad(!j.has(name),"Campo duplicado: "+name);j.add(name,JSON.fromJson(reader,JsonElement.class));}
            reader.endObject();bad(reader.peek()==com.google.gson.stream.JsonToken.END_DOCUMENT,"Conteúdo adicional após o JSON.");return j;
        }
        catch(JsonParseException|IllegalStateException|IOException e){throw fail(400,"DADOS_INVALIDOS","JSON inválido.");}
    }
    public static void fields(JsonObject j,String... names){Set<String> set=Set.of(names);for(String k:j.keySet())bad(set.contains(k),"Campo desconhecido: "+k);}
    public static String text(JsonObject j,String k,int max,boolean optional){
        if(!j.has(k)){bad(optional,"Informe "+k+".");return null;}
        JsonElement e=j.get(k);bad(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString(),"Campo "+k+" deve ser texto.");
        String value=e.getAsString().trim();bad(!value.isEmpty()&&value.codePointCount(0,value.length())<=max,"Campo "+k+" vazio ou muito longo.");return value;
    }
    public static int id(String value){try{int n=Integer.parseInt(value);bad(n>0&&value.matches("[1-9][0-9]*"),"ID inválido.");return n;}catch(NumberFormatException e){throw fail(400,"DADOS_INVALIDOS","ID inválido.");}}
    public static Integer numberId(JsonObject j,String k,boolean optional){
        if(!j.has(k)){bad(optional,"Informe "+k+".");return null;}
        JsonElement v=j.get(k);bad(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isNumber(),k+" deve ser inteiro JSON.");return id(v.getAsString());
    }
    public static BigDecimal money(JsonObject j,String k,boolean optional){
        if(!j.has(k)){bad(optional,"Informe "+k+".");return null;}
        JsonElement e=j.get(k);bad(e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber(),k+" deve ser um número JSON.");
        try{BigDecimal v=e.getAsBigDecimal();bad(v.signum()>0&&v.stripTrailingZeros().scale()<=2&&v.compareTo(new BigDecimal("9999999999.99"))<=0,"Valor deve ser positivo, com até duas casas decimais.");return v.setScale(2);}
        catch(NumberFormatException|ArithmeticException ex){throw fail(400,"DADOS_INVALIDOS","Valor monetário inválido.");}
    }
    public static Instant instant(String v){try{return OffsetDateTime.parse(v).toInstant();}catch(Exception e){throw fail(400,"DADOS_INVALIDOS","Data deve seguir ISO 8601 com fuso horário.");}}
    public static Map<String,String> query(HttpExchange x,String... allowed){
        Map<String,String> q=new HashMap<>();String raw=x.getRequestURI().getRawQuery();
        if(raw!=null&&!raw.isBlank())for(String pair:raw.split("&")){String[] a=pair.split("=",2);String k=URLDecoder.decode(a[0],StandardCharsets.UTF_8);String v=URLDecoder.decode(a.length>1?a[1]:"",StandardCharsets.UTF_8);bad(!q.containsKey(k),"Parâmetro duplicado.");q.put(k,v);}
        bad(Set.of(allowed).containsAll(q.keySet()),"Parâmetro desconhecido.");return q;
    }
    public static void origin(HttpExchange x){require(ORIGIN.equals(x.getRequestHeaders().getFirst("Origin")),403,"ORIGEM_NAO_AUTORIZADA","Origem não autorizada.");}
    public static void internal(HttpExchange x){require(!KEY.isEmpty()&&java.security.MessageDigest.isEqual(KEY.getBytes(StandardCharsets.UTF_8),Objects.toString(x.getRequestHeaders().getFirst("X-ServiHUB-Internal"),"").getBytes(StandardCharsets.UTF_8)),403,"CHAMADA_INTERNA_NAO_AUTORIZADA","Chamada interna não autorizada.");}
    public static String cookie(HttpExchange x){String all=Objects.toString(x.getRequestHeaders().getFirst("Cookie"),"");for(String part:all.split(";")){String s=part.trim();if(s.startsWith("servihubSessao="))return s.substring(15);}return "";}
    public static String cookieHeader(HttpExchange x){return "servihubSessao="+cookie(x);}
    public static Connection db()throws SQLException {
        Properties p=new Properties();p.setProperty("user",env("DB_USER","servihub"));p.setProperty("password",env("DB_PASSWORD",""));p.setProperty("connectTimeout","3000");p.setProperty("socketTimeout","10000");
        return DriverManager.getConnection(env("DB_URL","jdbc:mysql://127.0.0.1:3306/servihub?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"),p);
    }
    public interface Transaction<T>{T run(Connection c)throws Exception;}
    public static <T>T tx(Transaction<T> action)throws Exception {
        try(Connection c=db()){c.setAutoCommit(false);try{T v=action.run(c);c.commit();return v;}catch(Exception e){c.rollback();throw e;}finally{try{one(c,"SELECT RELEASE_LOCK('servihub_agenda') AS released");}catch(SQLException ignored){/* Closing the physical connection also releases its named lock. */}}}
    }
    public static void params(PreparedStatement p,Object...args)throws SQLException {
        for(int i=0;i<args.length;i++){Object a=args[i];if(a instanceof Instant)p.setTimestamp(i+1,Timestamp.from((Instant)a),Calendar.getInstance(TimeZone.getTimeZone("UTC")));else p.setObject(i+1,a);}
    }
    public static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException {
        try(PreparedStatement p=c.prepareStatement(sql)){p.setQueryTimeout(8);params(p,args);try(ResultSet r=p.executeQuery()){
            List<Map<String,Object>> list=new ArrayList<>();ResultSetMetaData m=r.getMetaData();while(r.next()){
                Map<String,Object> v=new LinkedHashMap<>();for(int i=1;i<=m.getColumnCount();i++){Object a=r.getObject(i);if(a==null)continue;
                    if(a instanceof Timestamp)a=((Timestamp)a).toInstant().toString();else if(a instanceof java.sql.Date)a=a.toString();else if(a instanceof LocalDateTime)a=((LocalDateTime)a).toInstant(ZoneOffset.UTC).toString();else if(a instanceof OffsetDateTime)a=((OffsetDateTime)a).toInstant().toString();
                    v.put(m.getColumnLabel(i),a);}list.add(v);}return list;
        }}
    }
    public static Map<String,Object> one(Connection c,String sql,Object...args)throws SQLException {
        // MySQL returns generated keys through JDBC instead of SQL RETURNING.
        int marker=sql.indexOf(" RETURNING ");
        if(marker>=0){String statement=sql.substring(0,marker),projection=sql.substring(marker+11);String table;long id;
            if(statement.startsWith("INSERT INTO ")){
                table=statement.substring(12).split("[ (]",2)[0];
                try(PreparedStatement p=c.prepareStatement(statement,Statement.RETURN_GENERATED_KEYS)){p.setQueryTimeout(8);params(p,args);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){if(!r.next())throw new SQLException("Generated key not returned");id=r.getLong(1);}}
            }else if(statement.startsWith("UPDATE ")){table=statement.substring(7).split(" ",2)[0];update(c,statement,args);id=((Number)args[args.length-1]).longValue();}
            else throw new SQLException("Unsupported write statement");
            return one(c,"SELECT "+projection+" FROM "+table+" WHERE id=?",id);
        }
        List<Map<String,Object>> a=rows(c,sql,args);return a.isEmpty()?null:a.get(0);
    }
    public static Map<String,Object> found(Connection c,String table,int id,String code)throws SQLException {
        Set<String> permitted=Set.of("cliente","endereco","servico","agendamento","orcamento","pagamento","catalogo_servico");if(!permitted.contains(table))throw new IllegalArgumentException();
        Map<String,Object> r=one(c,"SELECT * FROM "+table+" WHERE id=?",id);require(r!=null,404,code,"Registro não encontrado.");return r;
    }
    public static void update(Connection c,String sql,Object...args)throws SQLException {try(PreparedStatement p=c.prepareStatement(sql)){p.setQueryTimeout(8);params(p,args);p.executeUpdate();}}
    public static void lockAgenda(Connection c)throws SQLException {Map<String,Object> lock=one(c,"SELECT GET_LOCK('servihub_agenda',5) AS acquired");require(lock!=null&&lock.get("acquired") instanceof Number&&((Number)lock.get("acquired")).intValue()==1,503,"AGENDA_TEMPORARIAMENTE_INDISPONIVEL","A agenda está ocupada. Consulte novamente.");}
    public static boolean bool(JsonObject j,String key){bad(j.has(key)&&j.get(key).isJsonPrimitive()&&j.get(key).getAsJsonPrimitive().isBoolean(),"Informe "+key+" como booleano.");return j.get(key).getAsBoolean();}
    public static final String CATALOGO="id AS \"catalogoId\",nome,descricao,preco_padrao AS \"precoPadrao\",duracao_minutos AS \"duracaoMinutos\"";
    public static final String CONFIG="nome_empresa AS \"nomeEmpresa\",usar_clientes AS \"usarClientes\",usar_agenda AS \"usarAgenda\",usar_orcamentos AS \"usarOrcamentos\",usar_pagamentos AS \"usarPagamentos\"";
    public static final String CLIENTE="id AS \"clienteId\",nome,telefone,email,criado_em AS \"criadoEm\"";
    public static final String ENDERECO="id AS \"enderecoId\",cliente_id AS \"clienteId\",logradouro,numero,complemento,bairro,cidade,uf,cep,criado_em AS \"criadoEm\"";
    public static final String SERVICO="s.id AS \"servicoId\",s.cliente_id AS \"clienteId\",s.endereco_id AS \"enderecoId\",s.catalogo_id AS \"catalogoId\",s.catalogo_id AS \"catalogoId\",s.descricao,s.valor_previsto AS \"valorPrevisto\",s.status,s.criado_em AS \"criadoEm\",s.atualizado_em AS \"atualizadoEm\"";
    public static final String ORCAMENTO="id AS \"orcamentoId\",cliente_id AS \"clienteId\",servico_id AS \"servicoId\",descricao,valor_total AS \"valorTotal\",status,atualizado_em AS \"atualizadoEm\",criado_em AS \"criadoEm\"";
    public static final String PAGAMENTO="id AS \"pagamentoId\",servico_id AS \"servicoId\",valor,situacao,vencimento_em AS \"vencimentoEm\",pago_em AS \"pagoEm\",atualizado_em AS \"atualizadoEm\"";
    private Common(){}
}
