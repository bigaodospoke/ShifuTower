# ShifuTower

Torre de Batalha para **Cobblemon 1.8.1** (Fabric, Minecraft 1.21.1).

O jogador sobe andares enfrentando treinadores com **times aleatórios** que ficam mais fortes a cada andar, até o **Mestre da Torre** no topo, com um time inteiro de **lendários Uber/AG**. Quem vence ganha um **troféu 3D** com o nome e o tempo gravados, entra no **ranking por tempo** e acumula **Battle Points (BP)** para gastar numa **loja própria**. Cada jogador pode desafiar a torre **1 vez por dia**.

## Instalação

O mod precisa estar **no servidor e no cliente** (tem bloco, GUI, HUD e balão de fala).

Dependências (as mesmas que o servidor já usa):

- Fabric Loader 0.16+ e Fabric API
- Fabric Language Kotlin
- Cobblemon 1.8.x

Jar compilado: `build/libs/shifutower-1.0.0.jar`.

## Como funciona

1. Um admin cria a recepcionista da torre no spawn com `/shifutower recepcionista`, ou o jogador usa `/torre`.
2. Na tela da torre, o jogador clica em **INICIAR DESAFIO**.
3. Ele é levado para uma dimensão própria (`shifutower:torre`), com uma arena por andar empilhada de 20 em 20 blocos. Ele realmente vê a torre subindo.
4. Em cada andar um treinador aparece, fala com ele (balão de fala sobre a cabeça, com o efeito de "digitação" e o som de fala do Cobblemon) e a batalha começa.
5. Vencer dá BP e leva ao próximo andar. Perder ou desistir encerra o desafio e devolve o jogador para onde ele estava.
6. Ao vencer o Mestre da Torre: troféu no inventário, anúncio no chat, posição no ranking e tela de vitória com o troféu girando.

O time real do jogador nunca é alterado: as batalhas usam uma cópia curada do time (sem perder HP/PP e sem mudar nível).

### Dificuldade (padrão, 10 andares)

| Andares | Faixa | Tier Smogon | Pokémon | Nível | IVs | EVs | IA |
|---|---|---|---|---|---|---|---|
| 1–2 | Iniciante | PU/ZU | 3 | 55–60 | 10–20 | não | 1 |
| 3–4 | Intermediário | NU | 4 | 65–70 | 15–25 | não | 2 |
| 5–6 | Avançado | RU | 5 | 75–80 | 20–31 | sim | 3 |
| 7–8 | Elite | UU | 6 | 85–90 | 25–31 | sim | 4 |
| 9 | Mestre | OU | 6 | 95 | 31 | sim | 5 |
| 10 | **Chefe** | Uber/AG (lendários) | 6 | 100 | 31 | sim | 5 |

Os times são sorteados a cada desafio: espécie, 4 golpes do movepool competitivo (tirado dos sets de Random Battle do Showdown), item, natureza e habilidade.

**Lendários sem modelo:** no Cobblemon 1.8.1 puro, só Mewtwo, Lugia, Ho-Oh, Rayquaza, Xerneas e Mew têm modelo. O chefe usa primeiro os lendários com modelo. Se o servidor tiver addons que implementam outros (Kyogre, Groudon, Zacian...), eles entram no sorteio automaticamente. Para desligar essa preferência, use `"preferirPokemonComModelo": false`.

## Comandos

### Jogadores
| Comando | O que faz |
|---|---|
| `/torre` | Abre a tela da torre |
| `/torre ranking` | Abre direto no ranking |
| `/torre loja` | Abre direto na loja |
| `/torre desistir` | Desiste do desafio atual |

### Admin (`/shifutower`, permissão 2)
| Comando | O que faz |
|---|---|
| `recepcionista` | Cria a NPC recepcionista onde você está (clique nela para abrir a torre) |
| `recarregar` | Recarrega todos os arquivos de configuração |
| `loja adicionar <preço> [limite]` | Coloca o item da sua mão na loja, com encantamentos, nome e lore |
| `loja remover <nº>` / `loja listar` | Gerencia a loja |
| `bp ver/adicionar/remover/definir <jogador> [qtd]` | Gerencia Battle Points |
| `resetar <jogador>` | Libera mais uma tentativa hoje |
| `ranking limpar` / `ranking remover <jogador>` | Gerencia o ranking |
| `trofeu <jogador>` | Entrega um troféu |
| `testar <andar>` | Mostra no chat um time sorteado para aquele andar |
| `arena <andar>` | Monta a arena e um treinador para você ver, sem batalha |
| `voltar` | Sai da pré-visualização da arena |
| `validar` | Confere espécies, golpes, habilidades e itens de `times.json` e da loja |
| `encerrar <jogador>` | Encerra o desafio de alguém e devolve a tentativa |

## Configuração (`config/shifutower/`)

- **`torre.json`**: número de andares, tentativas por dia, fuso horário, BP por andar, bônus de conclusão, nível forçado na batalha, espécies proibidas, nível máximo do jogador e as faixas de dificuldade.
- **`times.json`**: grupos de Pokémon por faixa. Cada set tem `especie`, `golpes` (o mod sorteia 4), `itens`, `naturezas`, `habilidades`, `evs`, `extra` (propriedades do Cobblemon, ex.: `"aspect=alolan"`) e `peso`.
- **`treinadores.json`**: tipos de treinador (título, nomes, skin, em quais andares aparecem) e as falas de início, vitória e derrota. Placeholders: `{jogador}`, `{andar}`, `{total}`. A skin pode ser `shifutower:<id>` (skins do mod) ou `jogador:<Nick>` (skin de um jogador; ex.: deixe o Mestre da Torre com a sua skin).
- **`loja.json`**: itens da loja (`item`, `quantidade`, `preco`, `limitePorJogador`, `nome` com cores `&`, `descricao`, `comandos` executados na compra com `{jogador}`, `somenteComandos`).

Os dados dos jogadores (BP, tentativas, recordes, compras) ficam em `<mundo>/shifutower/jogadores.json`.

## Ferramentas

- `node tools/gerar_texturas.js`: regera troféu, ícones da GUI, skins dos NPCs e ícone do mod (desenhados por código, sem dependências).
- `node tools/gerar_times.js <pasta showdown> <species.json> <saida>`: regera `times.json` a partir dos tiers e sets do Showdown.

## Compilar e testar

```
./gradlew build
```

Teste automático do lado servidor (sobe um servidor dedicado com Cobblemon, roda `validar`, `testar` e `arena` pelo console e confere blocos/NPCs):

```
bash run/selftest.sh run/test-output.log | ./gradlew runServer
```

O `build.gradle.kts` coloca o ICU4J no classpath do `runServer`: o Loom tira essa biblioteca (que o Minecraft só usa no cliente), mas o Showdown do Cobblemon precisa dela. Em produção isso não é necessário, porque o jar do Cobblemon traz a própria cópia.
