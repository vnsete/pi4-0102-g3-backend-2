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

Não tem.

## Saída

Sucesso: HTTP 200.

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| pendencias | lista de objeto Pendencia | sim | ver objeto Pendencia |

### Objeto Pendencia

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| pagamentoId | inteiro | sim | 50 |
| servicoId | inteiro | sim | 20 |
| clienteId | inteiro | sim | 1 |
| nomeCliente | texto | sim | Carlos Mendes |
| descricao | texto | sim | Limpeza da piscina |
| valor | decimal | sim | 150.00 |
| situacao | um de: pendente, pago | sim | pendente |
| atualizadoEm | data e hora | sim | 2026-10-13T09:05:00-03:00 |

## Cabeçalhos de entrada

| Campo | Tipo | Obrigatório | Exemplo |
| --- | --- | --- | --- |
| Cookie servihubSessao | texto | sim | 64 caracteres hexadecimais do token |

## Regras

Backend 2 chama CT18 e conserva os mesmos campos e tipos, devolvendo como pendencias. Frontend sinaliza cada registro e seu valor com situação pendente. Consulta ao abrir a tela e após salvar um pagamento; não há notificação externa. O Backend 2 encaminha o cookie servihubSessao ao Backend 1. Não possui cópia da sessão e não decide se a conta está autorizada. Respostas 401 ou 403 do Backend 1 são repassadas com o mesmo código e corpo. Outros erros ou corpo incompatível geram 503 GESTAO_INDISPONIVEL. Nenhuma indisponibilidade vira lista vazia e nenhum dado antigo é apresentado como atualizado. Regras de sessão, cabeçalhos, origem, timeout e limites seguem a visão geral. O corpo JSON não contém o token de sessão. Resposta contém Content-Type: application/json e Cache-Control: no-store.

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

