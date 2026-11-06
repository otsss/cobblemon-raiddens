# Validação da migração de combate

## Escopo

Etapa 1 (auditoria) e preparação do núcleo da etapa 2, na branch
`feature/traditional-world-boss`. Ainda não existe um adaptador de combate
tradicional conectado ao núcleo. O JAR desta etapa mantém o combate automático
legado; não é uma versão oficial nem deve substituir o servidor de produção.

O destino autorizado para esta branch é `otsss/cobblemon-raiddens`. O histórico
Shirokai é preservado nesta branch separada, sem substituir a `master` do Raid
Dens. A tentativa anterior de envio a `istwi/Shirokai-Raids` foi recusada por
permissão; esse repositório permanece apenas como origem histórica.

Fontes: Raid Dens 0.13.4 e HEAD 0.13.5 comparados; Cobblemon 1.8.3 e SkiesClear
1.1.2 inspecionados. Identidades, evidências e decisão estão em
[COBBLEMON_MULTIPLAYER_AUDIT.md](COBBLEMON_MULTIPLAYER_AUDIT.md).

## Ambiente e reprodução

Linux, JDK Temurin 21.0.13+9, Gradle wrapper 8.14.3 com checksum versionado,
Fabric Loom 1.10.5 e Cobblemon Maven 1.8.3+1.21.1.
O JRE inicial não tinha javac; `scripts/setup-cloud.sh` instalou o JDK completo
e verificou o arquivo pelo checksum oficial antes de usar.

O host artefacts.cobblemon.com retornou 403 no proxy. Foi adicionado ao build o
destino oficial alternativo maven.impactdev.net, restrito ao grupo com.cobblemon.
O POM retorna gitCommit `ffe005511dd0d3ba68377fa3f95906b3837d9a0e`.
SHA-256 do JAR Cobblemon resolvido:
`4bc3b09d25b93b74a5822dc35b06bd6f5fa8bae36ed43a8d2b3904156a61269c`.
Nenhuma versão de Cobblemon foi alterada para contornar a resolução.

```sh
cd /workspace/Shirokai-Raids
bash scripts/setup-cloud.sh
# Em instalações já preparadas:
source scripts/cloud-env.sh
bash gradlew --no-daemon build
python3 scripts/verify_jar.py --version 0.1.0-dev
```

Use o checkout existente neste ambiente isolado; não criar worktree sem pedido.
O cache padrão é /workspace/.gradle, gravável na máquina; o proxy e a confiança
TLS são preservados. O script não executa publicação ou gera dados do jogo.

## Testes locais

O primeiro build com JRE falhou por ausência de compilador. Após instalar o JDK,
o build passou. No build final executaram **26 testes**, com **0 falhas, 0 erros
e 0 skips**: 12 de ledger, 6 de núcleo legado, 3 de recompensas/tipos legados e
5 de persistência/configuração. Relatórios em build/test-results/test.

`scripts/verify_jar.py` validou metadados Fabric, dependência exata Cobblemon
1.8.3+1.21.1 e bytecode Java 21. SHA-256 do JAR desta etapa:
`64155c9681acc7e0fd32267d9959b49624a3ddcfba38e0cbf0e59ad0cb31a643`.
Essa validação estrutural não demonstra funcionamento do novo combate.

`RaidBattleLedgerTest` verifica:

- Três sessões isoladas alimentam 10.000.000 HP; após 100.000, 150.000 e 200.000,
  restam 9.550.000. Contribuição usa o total efetivo de 450.000.
- Replay, lacuna de sequência, raid antiga e sessão inexistente não aplicam dano.
- Overkill de Long.MAX_VALUE aplica somente o restante e dispara uma derrota.
- Cem callbacks duplicados concorrentes não duplicam dano/finalização.
- Desconexão preserva dano, libera slot e rejeita callbacks da sessão fechada.
- Reuso de sessão/cópia de boss e duas sessões do mesmo jogador são recusados.
- Cancelamento não produz vitória; snapshot é imutável.
- Contabilidade para 1, 3, 10, 25, 50 e 100 registros.

`PersistenceTest` também verifica padrão de limite 100, leitura da chave antiga
maximumParticipants, leitura da nova maxConcurrentPlayers, preservação de valores
personalizados e rejeição de limites acima de 100.

Esses casos não medem TPS, MSPT, memória por batalha ou latência de Showdown.
Não executam clientes, seleções de movimento, PP, troca ou interação R.

## Pendências de aceitação

- Adaptador real de batalha e protocolo de dano, inclusive isolamento da entidade
  pública e das cópias sem criar bosses extras no mundo.
- Teste interativo com três clientes (indisponíveis, conforme administrador).
- Remoção do modo automático após substituir seu fluxo por batalhas reais.
- Arena/lobby/teleporte, chunks, tag singular SkiesClear, bloqueios de captura,
  sorteios em duas etapas e entrega automática/menu seguro.
- Testes reais SkiesClear 1.1.2 Fabric, limpeza manual e automática com controle.
- Carga real/fiel nos seis patamares; capacidade de 100 combates não comprovada.
- Execução Linux e Windows no GitHub Actions desta branch; build local Linux
  não substitui verificação remota Windows.

Nenhuma tag ou Release v1.0.0 foi criada. O GTS-Shirokai não foi acessado nem alterado.

## Arquivos desta etapa

- build.gradle: destino Maven oficial alternativo do Cobblemon.
- scripts/cloud-env.sh: cache Gradle gravável e reutilizável.
- RaidConfig.java: nome maxConcurrentPlayers, padrão 100 e compatibilidade legada.
- core/RaidBattleLedger.java: HP e atribuição por sessão, implementação original.
- core/RaidBattleLedgerTest.java e PersistenceTest.java: invariantes e migração.
- README.md, esta validação e COBBLEMON_MULTIPLAYER_AUDIT.md: estado, evidências e limites.
