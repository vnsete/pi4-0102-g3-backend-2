
# CT17 listarPendencias

| Linha | Valor |
| --- | --- |
| ID e nome | CT17 listarPendencias |
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 2 |
| Atende | E10 |
| Como chamar | GET /pendencias |

## Entrada

Não possui parâmetros obrigatórios nem corpo de requisição.

## Saída

Sucesso: HTTP 200.

A API retorna diretamente um array JSON contendo os pagamentos pendentes.

| Campo | Tipo | Descrição |
| --- | --- | --- |
| id | inteiro | Identificador do pagamento |
| servico_id | inteiro | Identificador do serviço |
| valor | decimal | Valor registrado |
| situacao | texto | pendente |
| data_pagamento | data ou nulo | Data registrada no pagamento |
| descricao | texto | Descrição do serviço |
| cliente | texto | Nome do cliente |

## Cabeçalhos de entrada

| Campo | Tipo | Obrigatório |
| --- | --- | --- |
| Cookie | texto | Sim, para autenticação por sessão |

O navegador utiliza o cookie `servihubSessao`.

## Regras

O endpoint `GET /pendencias` do Backend 2 consulta os pagamentos pendentes por meio do Backend 1.

Primeiro, o Backend 2 encaminha o cookie recebido para `GET /sessao` no Backend 1, validando a autenticação.

Se a sessão não estiver autenticada, retorna HTTP 401 com o código `SESSAO_INVALIDA`.

Após a validação, o Backend 2 realiza uma requisição HTTP GET para `/pendencias` no Backend 1, utilizando o cabeçalho `X-ServiHUB-Internal` com a chave interna configurada.

O Backend 1 consulta as tabelas `pagamento`, `servico` e `cliente`, retornando somente os registros cuja situação seja `pendente`.

O Backend 2 repassa ao Frontend o código HTTP e o corpo recebidos dessa consulta.

Quando não existem pagamentos pendentes, retorna um array JSON vazio.

O Backend 2 não armazena uma cópia dos pagamentos nem mantém sessões próprias.

As requisições ao Backend 1 possuem timeout de 10 segundos.

Falhas de comunicação, respostas inválidas na validação da sessão ou exceções de integração resultam em HTTP 502 com o código `INTEGRACAO_INDISPONIVEL`.

A resposta utiliza Content-Type `application/json; charset=utf-8`.

## Erros

| Quando | O que volta |
| --- | --- |
| Sessão não autenticada | HTTP 401 — SESSAO_INVALIDA |
| Backend 1 indisponível ou falha de integração | HTTP 502 — INTEGRACAO_INDISPONIVEL |
| Rota inexistente | HTTP 404 — ROTA_NAO_ENCONTRADA |
| Método diferente de GET | HTTP 405 — METODO_NAO_PERMITIDO |
| Backend 1 retorna erro na consulta de pendências | Código HTTP e corpo repassados pelo Backend 2 |

## Objeto de erro

Os erros gerados pelo Backend 2 utilizam um objeto JSON com o campo `erro`.

Exemplo:

```json
{
  "erro": "INTEGRACAO_INDISPONIVEL"
}
```
