# CT9: consultarPendencias

| Linha | Valor |
| :--- | :--- |
| **Fronteira** | HTTP |
| **Quem chama** | Frontend |
| **Quem responde** | Backend 2 |
| **Atende** | E7 e E10 |
| **Como chamar** | `GET /pagamentos/pendentes` |

## Entrada
| Campo | Tipo | Obrigatório | Exemplo |
| :--- | :--- | :--- | :--- |
| "não tem" | "não tem" | não | "não tem" |

## Saída
| Campo | Tipo | Obrigatório | Exemplo |
| :--- | :--- | :--- | :--- |
| pendencias | lista de (objeto) | sim | lista de pagamentos com status pendente |

## Erros
| Quando | O que volta |
| :--- | :--- |
| Falha na consulta | Erro `BANCO_INDISPONIVEL` |
