# Auditoria de World Boss com batalhas tradicionais

## Estado desta etapa

Branch: `feature/traditional-world-boss`, a partir de `07a5443`.
Decisão de arquitetura tomada; integração de combate ainda não validada.
O código anterior de ataques automáticos continua sendo uma base histórica, não
uma implementação aceitável do requisito novo. O novo `RaidBattleLedger` é apenas
o núcleo de contabilidade; ainda não está conectado a Cobblemon/Showdown.
Não instalar um build desta etapa esperando batalhas tradicionais ou 100 clientes.

O administrador confirmou SkiesClear **1.1.2 Fabric** e informou que ainda não há
três clientes disponíveis. A validação interativa da etapa 2 continua pendente.
Não há evidência de capacidade real de 1, 3 ou 100 sessões de combate nesta etapa.

## Referências fixadas e identidade do binário

| Referência | Identidade inspecionada |
| --- | --- |
| Shirokai | `istwi/Shirokai-Raids`, base `07a5443`, histórico preservado |
| Raid Dens 0.13.4 | commit `59eddf929451cd86256ad0a3a880f07f10fb1661`, Minecraft 1.21.1, compilado para Cobblemon 1.8.2 |
| Checkout Raid Dens disponível | `958360b25f10620e4705ecba7fb6ed83d582f6d3`, 0.13.5/Cobblemon 1.8.4; mensagem registra remoção temporária de battle sync e scripts |
| Cobblemon | tag 1.8.3, commit `ffe005511dd0d3ba68377fa3f95906b3837d9a0e` |
| SkiesClear | versão fonte 1.1.2, commit `19a95b4e5c063487884d3237d282d085f452e698` |

JAR fornecido: `cobblemonraiddens-fabric-0.13.4+1.21.1.jar`.
SHA-256: `8caa1912a0d51dff2555855e054bec6606a3b148232643c0ba6c1c6ae5a35dff`.
Metadados declaram MIT License, Cobblemon >=1.8.0 e GeckoLib >=4.7.0.
Isso é uma declaração de dependência, não prova de compatibilidade com 1.8.3.
Não há atestado de build que vincule o JAR integralmente a um commit. A comparação
de bytecode de `RaidBuilder.build` confirmou a construção do ator selvagem com
`getPokemon()` como original e effected, sem uma cópia por sessão nesse trecho.
Não inferimos equivalência completa do binário com o commit pela versão.

Fontes auditadas:

- [Raid Dens 0.13.4](https://github.com/necro50n3/cobblemon-raiddens/tree/59eddf929451cd86256ad0a3a880f07f10fb1661)
- [Cobblemon 1.8.3](https://gitlab.com/cable-mc/cobblemon/-/tree/ffe005511dd0d3ba68377fa3f95906b3837d9a0e)
- [SkiesClear 1.1.2](https://github.com/PokeSkies/SkiesClear/tree/19a95b4e5c063487884d3237d282d085f452e698)

## Como o Raid Dens funciona

Os caminhos abaixo são relativos à raiz do commit **0.13.4**, salvo indicação.

| Questão | Evidência técnica | Implicação para Shirokai |
| --- | --- | --- |
| Iniciar batalha | `common/.../raids/RaidBuilder.java`, `build`: equipe real via `toBattleTeam(false,false,leadingPokemon)`, `PlayerBattleActor`, `PokemonBattleActor`, GEN_9_SINGLES e `BattleRegistry.startBattle` | Usa Showdown e a seleção normal de movimentos; não é cálculo simplificado de poder |
| Interação normal | `mixins/client/PartySendBindingMixin.java`: intercepta `processEntityTarget` e envia `RAID_CHALLENGE`; `network/packets/RaidChallengePacket.java` valida alvo/equipe e chama builder | O Raid Dens usa código cliente e pacote próprio; seu caminho não pode simplesmente ser copiado para um mod somente servidor |
| Multiplayer | `RaidInstance.addBattle` mantém uma lista de `PokemonBattle`; cada participante tem sessão independente | Não é uma única batalha de 100 atores; o limite de atores de uma sessão não mede o limite de sessões no servidor |
| HP compartilhado | `RaidInstance.syncHealth` mantém `currentHealth`/`maxHealth`, aplica clamp e envia `BattleHealthChangePacket` às sessões | A BossBar e os pacotes são visualização; não bastam para sincronizar o HP no simulador |
| Dano | `.../showdown/conditions/raidboss.js`, `onDamage`, emite mensagens split `-raiddamage` com valor exato privado e percentual público e retorna 0; `RaidDensShowdownRegistry.registerInstructions` registra parser; `RaidDamageInstruction.postActionEffect` envia o dano a `syncHealth` | Contabilizar somente uma ocorrência autoritativa; jamais somar as cópias pública e privada |
| Estado Showdown | `StartRaidShowdownEvent` usa `>eval` para adicionar volatile `raidboss`; `ShowdownLoader` instala condições/index.js no diretório Showdown | Copiar a árvore inteira sobrescreveria arquivos compartilhados e carregaria compatibilidades desnecessárias |
| Participantes | `RaidInstance` mantém playerMap, activePlayers, failedPlayers, battles e damageTracker; `RaidChallengePacket` valida participante, vida, equipe, distância e limite | Sessão, jogador e raid precisam de identidades distintas; entrada tardia não deve zerar contribuição anterior |
| Desconexão | Fabric `events/ModEvents.onPlayerDisconnect` encaminha ao `RaidJoinHelper.onPlayerDisconnect`; este reembolsa fila e chama `RaidUtils.leaveRaid`; `RaidInstance.removePlayer` remove sessão e aplica regras de vidas | No World Boss público, desconectar deve liberar apenas a sessão, preservar contribuição e não cancelar outros jogadores |
| Derrota | `syncHealth` agenda `queueStopRaid`; `stopRaid` encaminha para sucesso/falha e fechamento | Finalização deve ser uma transição única, invalidando eventos tardios antes de recompensar |
| Recompensas | `RaidInstance.handleSuccess` filtra contribuição, calcula elegibilidade de captura, cria Pokémon de recompensa e emite RAID_END; `RewardResponsePacket` encaminha entrega | Não reutilizar este fluxo: Shirokai não terá captura, Raid Pouch ou recompensa em Pokémon |
| Limite | `RaidBoss.max_players`, padrão 4 nos TierConfig; `RaidChallengePacket` compara `raid.getBattles().size()` com `getMaxPlayers()` | O padrão 4 é configurável no Raid Dens, não uma demonstração de limite do Showdown; aumentá-lo não comprova 100 sessões |
| Captura | Bosses usam propriedades/estado de raid; `handleSuccess` tem caminho explícito de Pokémon capturável/recompensa e `max_catches` | `max_catches=0` sozinho não substitui bloqueios de captura no spawn, eventos e cópias de batalha |

### Riscos concretos de incorporação direta

1. `RaidBuilder` coloca o mesmo Pokémon do boss como original/effected em todas
   as sessões. O Shirokai não pode repetir isso: `BattleRegistry.startShowdown`
   atribui `battleId` às entidades dos atores e `PokemonBattleActor.sendUpdate`
   limpa esse ID no fim. Uma sessão pode interferir no ciclo de outra.
2. `syncHealth` soma contribuição positiva dividida pelo máximo **antes** do clamp
   do HP. Logo, o trecho não limita contribuição ao dano efetivo restante.
   Não transplantar essa contabilidade para as recompensas.
3. Os números são `float`; o novo núcleo usa `long` para HP e contribuição.
4. O volatile upstream modifica mecânicas: impede movimentos como Explosion,
   Transform e Perish Song, além de tratar dano residual, cura, recoil e drain.
   Prometer todas as mecânicas tradicionais sem política explícita seria incorreto.
5. O HEAD 0.13.5 está em transição para 1.8.4; não é uma base segura para presumir
   que a sincronização de 0.13.4 funciona em 1.8.3.

## Cobblemon 1.8.3 e Showdown

Caminhos relativos a `common/src/main/kotlin/com/cobblemon/mod/common`:

- `net/serverhandling/ChallengeHandler.kt`: o pacote normal verifica interação,
  `canBattle` e chama `BattleBuilder.pve`. Este é o ponto de entrada a preservar.
- `entity/pokemon/PokemonEntity.kt`, `canBattle`: rejeita UNBATTLEABLE, busy,
  entidade em batalha e outras condições. Não basta remover UNBATTLEABLE do boss.
- `battles/BattleBuilder.kt`, `pve`: rejeita `pokemonEntity.battleId != null` e
  cria um `PokemonBattleActor` com o Pokémon original. Portanto, não permite
  naturalmente várias batalhas contra a mesma entidade.
- `battles/BattleRegistry.kt`, `startBattle`/`startShowdown`: mantém mapa de
  sessões, envia equipes ao simulador e atribui battleId às entidades. O método
  de baixo nível NÃO valida equipes/atores por conta própria.
- `battles/pokemon/BattlePokemon.kt`: `safeCopyOf` clona, aplica battle clone e
  uncatchable, mas seu ciclo de entidade/recall ainda deve ser adaptado. Não
  assumir que só usar esse método resolve posição, visibilidade e animações.
- `battles/interpreter/instructions/DamageInstruction.kt`: `-damage` informa HP
  resultante, não um delta global confiável. Alterações de cura, máximo ou mensagens
  repetidas tornam incorreto subtrair cegamente dois valores locais.
- `api/events/CobblemonEvents.kt`: há eventos de início, fim, fuga e desmaio;
  não foi encontrada ali uma API pronta de World Boss/HP global. Será necessária
  integração versionada ao simulador ou ao interpretador, não apenas ouvir vitória.
- `battles/runner/graal/GraalShowdownService.kt`: cria um contexto Graal e funções
  de comunicação do simulador. Não há aqui uma promessa de 100 sessões paralelas
  em threads independentes. Não chamar contexto, entidades ou inventários de
  workers arbitrários para tentar ganhar desempenho.

Escopo de compatibilidade confirmado nesta auditoria: símbolos e fluxos nas
fontes 1.8.3. Compatibilidade de mixins, protocolos, PP, efeitos, troca e UI exige
compilação e teste do adaptador no servidor e clientes reais.

## Comparação e decisão

| Alternativa | Benefício | Custo/risco | Decisão |
| --- | --- | --- | --- |
| Fork Raid Dens | Combate cooperativo, interfaces e conteúdo existentes | Remove muito conteúdo, mistura suporte a loaders/cliente e conserva acoplamento ao simulador; exige corrigir isolamento e atribuição | Não escolhido |
| Incorporar componentes selecionados | Mod independente e reaproveitamento de conceitos comprováveis | Auditoria por arquivo, adaptação para 1.8.3, isolamento de objetos e testes reais continuam obrigatórios | **Escolhido: arquitetura independente, núcleo original e adaptador mínimo** |
| Addon/API Raid Dens | Menos mudanças upstream e eventos já disponíveis | Exige JAR original, dependências e compatibilidade cliente; geração/conteúdo continuam registrados | Alternativa se o adaptador independente se mostrar inviável; depende de decisão explícita |

Não foi incorporado código Raid Dens neste conjunto de alterações. O novo núcleo
é implementação original de contabilidade. O JAR original é apenas referência;
não será empacotado nem declarado como dependência de produção.

### Licenças

`LICENSE` no commit 59eddf9 é MIT, copyright (c) 2026 necro50n3. Porém
`assets/cobblemonraiddens/showdown/index.js` contém cabeçalho **MPL-2.0**, copyright
2023 Cobblemon Contributors. A licença da raiz não apaga avisos por arquivo.
O Showdown e dependências embutidas também devem ter avisos próprios auditados
antes de qualquer redistribuição. Não assumimos que todo arquivo do JAR é MIT.
O arquivo LICENSE completo não foi encontrado na listagem do JAR fornecido;
metadados de licença não substituem preservação dos termos em uma distribuição.

Se código MIT for adaptado futuramente, incluir texto integral e copyright em
`THIRD_PARTY_NOTICES` e no JAR. Se algum arquivo MPL for modificado/distribuído,
preservar avisos, licença, disponibilidade de fonte e obrigações de nível de
arquivo. Não copiar `index.js` ou código de terceiros integralmente por conveniência.
Não alterar a licença do projeto Shirokai por inferência: a base não inclui uma
licença raiz explícita nesta revisão.

## Contrato do protótipo de combate (etapa 2)

1. Uma entidade pública de exibição, identificada por UUID + raidId; nenhum
   cristal, dimensão, estrutura ou instância de arena.
2. Interceptar somente o desafio **normal** ao boss controlado. Deixar os demais
   desafios intocados. Validar distância, dimensão, propriedade da equipe,
   faint/busy, permissões e capacidade na thread do servidor.
3. Criar uma cópia independente de Pokémon boss com UUID exclusivo por sessão.
   Nunca reusar a entidade pública como membro mutable de várias equipes.
   Resolver explicitamente posição/animação da cópia sem criar 100 bosses públicos.
4. Uma batalha Showdown singles real por jogador, equipe real completa, seleção
   manual, PP, precisão, efetividade e trocas processados pelo motor original.
5. Adaptador do simulador mantém o boss local vivo até decisão central. Emitir
   eventos autoritativos de dano com `(raidId, battleId, sequência)` uma única vez.
   Só enviar deltas finais calculados pelo motor; não aceitar dano do cliente.
   Não usar HP percentual público como dano, nem reaplicar o mesmo evento ao
   atualizar UI ou receber eco da sincronização.
6. `RaidBattleLedger` limita dano ao restante, agrega contribuição e declara uma
   derrota única. Toda entrada fecha imediatamente quando HP chega a zero.
7. Coalescer BossBar/HP visual por tick, evitando broadcast O(N) a cada golpe.
   Nenhum disco por ataque. Operações Minecraft permanecem na thread do servidor.
8. Derrota central encerra todas as sessões por snapshot; derrota/fuga individual
   fecha só a sessão. Desconexão preserva contribuição. Eventos atrasados de raid
   antiga são rejeitados. Nenhuma recompensa por cancelamento ou timeout.

**Limitações que precisam de política e testes, não de substituição silenciosa:**
status residual e sua atribuição, clima/terreno por sessão, cura do boss, recoil,
drain, OHKO, Perish Song, Destiny Bond, porcentagens do HP, transformação e
faint local. Inicialmente não afirmar compatibilidade integral dessas mecânicas.
O adaptador deverá rejeitar de forma explícita uma mecânica ainda incompatível,
com documentação, em vez de conceder dano ou vitória fictícios.

O ledger sozinho não impede reutilização de uma instância Java com UUID alterado:
o adaptador também precisa garantir isolamento de referências. Seus testes de
1/3/10/25/50/100 registros são de contabilidade, **não** simulações de Showdown.

## SkiesClear 1.1.2 Fabric

`EntityClearable.isBlacklisted` e `CobblemonClearable.isBlacklisted` verificam
`entity.tags`. `generateTags` reconhece `#tag=`. `Clearable.shouldClear` rejeita
blacklist antes de aplicar whitelist. Ambas as categorias podem alcançar Pokémon;
configurar em **todas** as limpezas habilitadas, automáticas e manuais:

```json
"blacklist": ["#tag=shirokai_raid_boss"]
```

Adicionar a entrada às listas existentes de `entities` **e** `cobblemon`, sem
substituir outros filtros. `clear_persistent=false` e `clear_named=false` são
camadas adicionais; a proteção não deve depender só deles.

A base Shirokai usa `shirokai_raids_boss` (plural), que **não corresponde** à tag
solicitada. Na etapa 4, aplicar a tag nova somente aos bosses controlados e manter
identificação suficiente para limpar entidades legadas sem alterar outros Pokémon.
Tag não bloqueia `/kill` ou outro mod que ignore blacklist. SkiesClear não será
modificado; os testes de limpeza precisam executar suas categorias e comandos
reais na versão instalada, mantendo um Pokémon controle removível.

## Persistência e recompensas: pontos de aceitação futuros

- Política inicial de restart pode cancelar raid ativa, sem recriar boss/recompensa.
  Manter ledger por UUID da raid, impedir dupla finalização, remover órfãos
  identificados e liberar tickets de chunk no fechamento.
- Sorteio em duas etapas: faixa pela contribuição **efetiva**, raridade pela
  distribuição da faixa e item ponderado dentro da raridade. Número inicial de
  rolls igual entre faixas, sem garantia de lendário. Bronze pode obter lendário;
  Diamante pode obter comum. Validar referências e IDs contra registros reais.
- Persistir o resultado do sorteio antes da entrega; nunca ressorteá-lo em retry.
  Offline/overflow ficam pendentes. Não jogar itens no chão ou entregar boss.
- Inventário e JSON não formam uma transação atômica. A base já mantém reservas
  incertas para revisão; não repetir automaticamente uma entrega de resultado
  desconhecido. Documentar possível retenção e recuperação administrativa.
- Menu deve reutilizar a mesma transação de resgate; clique repetido não cria
  nova recompensa. Comando de resgate é a alternativa permitida até menu seguro.

## Portas de validação

| Etapa | Evidência exigida antes de declarar completa |
| --- | --- |
| 1 Auditoria | Fontes/versionamento/licenças, comparação e contrato documentados aqui |
| 2 Protótipo | Três clientes: desafio normal, movimentos, troca, PP, efetividade, HP único, overkill, desconexão, derrota e fim das três telas |
| 3 Arena | setspawn/setlobby/spawninfo, lobby seguro em outra dimensão, sem blocos/instâncias/cristais |
| 4 Evento | Duas horas, timeout, chunks liberados, restart sem duplicação e limpeza SkiesClear real |
| 5 Recompensas | Tabelas por boss, sorteios independentes, inventário/offline/overflow e recuperação de reservas |
| 6 Carga | 1,3,10,25,50,100 sessões reais ou simulador fiel; TPS/MSPT/CPU/RAM/latência/erros e invariantes de HP |
| 7 Distribuição | Linux e Windows no Actions, JAR remapeado, SHA256 e roteiro de aceitação; sem v1.0.0 antecipada |

Ausência de clientes não impede implementação/testes locais, mas impede aprovar
o protótipo interativo ou inventar números de desempenho. Os testes do núcleo
não liberam uma versão oficial. Resultados executados são registrados em
`docs/VALIDACAO_MULTIPLAYER.md`.
