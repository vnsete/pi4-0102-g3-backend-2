# CT8: registrarPagamento

| Linha | Valor |
| :--- | :--- |
| **Fronteira** | HTTP |
| **Quem chama** | Frontend |
| **Quem responde** | Backend 2 |
| **Atende** | E9 |
| **Como chamar** | `POST /pagamentos` |

## Entrada
| Campo | Tipo | Obrigatório | Exemplo |
| :--- | :--- | :--- | :--- |
| orcamentoId | inteiro | sim | 5 |
| valorPago | decimal | sim | 350.00 |
| situacao | um de: pendente, pago | sim | pago |

## Saída
| Campo | Tipo | Obrigatório | Exemplo |
| :--- | :--- | :--- | :--- |
| id | inteiro | sim | 3 |
| situacao | um de: pendente, pago | sim | pago |
| atualizadoEm | data e hora | sim | 2026-10-13T19:25:00 |

## Erros
| Quando | O que volta |
| :--- | :--- |
| Orçamento não encontrado | Erro `ORCAMENTO_NAO_ENCONTRADO` |
