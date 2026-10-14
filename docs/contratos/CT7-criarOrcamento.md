# CT7: criarOrcamento

| Linha | Valor |
| :--- | :--- |
| **Fronteira** | HTTP |
| **Quem chama** | Frontend |
| **Quem responde** | Backend 2 |
| **Atende** | E8 |
| **Como chamar** | `POST /orcamentos` |

## Entrada
| Campo | Tipo | Obrigatório | Exemplo |
| :--- | :--- | :--- | :--- |
| clienteId | inteiro | sim | 42 |
| valorTotal | decimal | sim | 350.00 |
| descricao | texto | sim | Limpeza e troca de filtros |

## Saída
| Campo | Tipo | Obrigatório | Exemplo |
| :--- | :--- | :--- | :--- |
| id | inteiro | sim | 5 |
| valorTotal | decimal | sim | 350.00 |
| criadoEm | data e hora | sim | 2026-10-13T19:20:00 |

## Erros
| Quando | O que volta |
| :--- | :--- |
| Cliente inválido | Erro `CLIENTE_NAO_ENCONTRADO` |
