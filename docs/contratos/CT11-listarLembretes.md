# CT11 listarLembretes

| Linha | Valor |
| --- | --- |
| ID e nome | CT11 listarLembretes |
| Fronteira | HTTP |
| Quem chama | Frontend |
| Quem responde | Backend 2 |
| Atende | E7 |
| Como chamar | GET /lembretes |

## Entrada

Não tem.

## Saída

Sucesso: HTTP 200.

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| lembretes | lista de objeto Lembrete | sim | ver objeto Lembrete |

### Objeto Lembrete

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| agendamentoId | inteiro | sim | 30 |
| servicoId | inteiro | sim | 20 |
| clienteId | inteiro | sim | 1 |
| nomeCliente | texto | sim | Carlos Mendes |
| inicioEm | data e hora | sim | 2026-10-14T10:00:00-03:00 |
| mensagem | texto | sim | Visita a Carlos Mendes em 14/10/2026 às 10:00. |

## Cabeçalhos de entrada

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| Cookie servihubSessao | texto | sim | 64 caracteres hexadecimais do token |

## Regras

Backend 2 captura uma vez o instante atual e chama CT12 com de=agora e ate=agora+24 horas. Para cada atendimento retornado gera a mensagem e preserva os IDs e inicioEm. Retorna por inicioEm e agendamentoId; sem visitas próximas retorna lista vazia. Frontend consulta ao abrir a agenda e a cada 60 segundos enquanto ela estiver aberta e visível. Não há envio por WhatsApp, e-mail ou push. O Backend 2 encaminha o cookie servihubSessao ao Backend 1. Não possui cópia da sessão e não decide se a conta está autorizada. Respostas 401 ou 403 do Backend 1 são repassadas com o mesmo código e corpo. Outros erros ou corpo incompatível geram 503 GESTAO_INDISPONIVEL. Nenhuma indisponibilidade vira lista vazia e nenhum dado antigo é apresentado como atualizado. Regras de sessão, cabeçalhos, origem, timeout e limites seguem a visão geral. O corpo JSON não contém o token de sessão. Resposta contém Content-Type: application/json e Cache-Control: no-store.

Padrões de campos, datas, valores, rede e erros seguem docs/fluxos/visao-geral.md no Frontend. Campo recebido fora do contrato é rejeitado com 400 DADOS_INVALIDOS.

## Erros

| Quando | O que volta |
| --- | --- |
| Backend 1 indisponível, resposta inválida ou timeout de 5 segundos | 503 GESTAO_INDISPONIVEL |
| Cookie ausente, sessão expirada ou invalidada | 401 SESSAO_INVALIDA |
| ID, campo obrigatório, tipo ou JSON inválido | 400 DADOS_INVALIDOS |
| Falha inesperada | 500 ERRO_INTERNO |

### Objeto Erro

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| codigo | texto | sim | DADOS_INVALIDOS |
| mensagem | texto | sim | Confira os campos informados. |

