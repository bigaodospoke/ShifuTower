package com.shifutower.config

import com.google.gson.annotations.SerializedName

/** config/shifutower/treinadores.json — quem aparece na torre e o que cada um fala. */
class TrainersConfig {
    @SerializedName("treinadores")
    var trainers: List<TrainerArchetype> = defaultTrainers()

    @SerializedName("recepcionista")
    var receptionist: ReceptionistConfig = ReceptionistConfig()

    companion object {
        fun defaultTrainers(): List<TrainerArchetype> = listOf(
            TrainerArchetype(
                id = "as_treinador", title = "Ás Treinador",
                names = listOf("Lucas", "Rafael", "Bruno", "Diego", "Tiago", "Caio", "Mateus"),
                skin = "shifutower:as_treinador", minFloor = 1, maxFloor = 9,
                intro = listOf(
                    "Então você quer subir a Torre Shifu? Vai ter que passar por mim primeiro!",
                    "Andar {andar}... daqui você não passa, {jogador}!",
                    "Treinei a semana inteira para este momento. Prepare-se!",
                ),
                npcWins = listOf("Hah! Volte amanhã com um time melhor.", "A torre é alta demais para você... por enquanto."),
                npcLoses = listOf("Uau... você é forte mesmo. Pode subir!", "Tá bom, tá bom! O próximo andar te espera."),
            ),
            TrainerArchetype(
                id = "veterana", title = "Veterana",
                names = listOf("Helena", "Marta", "Carmem", "Lúcia", "Vera"),
                skin = "shifutower:veterana", minFloor = 3, maxFloor = 9,
                intro = listOf(
                    "Já vi muitos jovens chegarem até aqui. Poucos passaram.",
                    "Experiência vale mais que força bruta, {jogador}. Vou te mostrar.",
                ),
                npcWins = listOf("Paciência, jovem. A torre ensina quem persiste.", "Ainda falta muito chão... ou melhor, muito andar."),
                npcLoses = listOf("Hmpf... você me lembrou de quando eu era jovem. Siga em frente.", "Bela batalha. Faz tempo que não me divirto assim."),
            ),
            TrainerArchetype(
                id = "faixa_preta", title = "Faixa-Preta",
                names = listOf("Kenji", "Ryu", "Takeo", "Hiro", "Daichi"),
                skin = "shifutower:faixa_preta", minFloor = 1, maxFloor = 7,
                intro = listOf("Hiyaaa! Corpo e mente em perfeita harmonia!", "Meu treino diário é subir esta torre. Mostre sua força!"),
                npcWins = listOf("OSU! Treine mais e volte!", "Sua guarda estava aberta."),
                npcLoses = listOf("OSU... reconheço sua força.", "Hoje o discípulo superou o mestre... digo, eu."),
            ),
            TrainerArchetype(
                id = "cientista", title = "Cientista",
                names = listOf("Dr. Otávio", "Dra. Íris", "Prof. Silas", "Dr. Nilo"),
                skin = "shifutower:cientista", minFloor = 2, maxFloor = 8,
                intro = listOf(
                    "Calculei 94,7% de chance de vitória. A sua, claro, é o resto.",
                    "Vamos testar uma hipótese: você consegue passar do andar {andar}?",
                ),
                npcWins = listOf("Hipótese confirmada! Hehehe.", "Os dados não mentem."),
                npcLoses = listOf("Impossível! Preciso refazer meus cálculos...", "Interessante... muito interessante. Pode passar."),
            ),
            TrainerArchetype(
                id = "domadora_dragoes", title = "Domadora de Dragões",
                names = listOf("Dália", "Drácia", "Yara", "Safira", "Kira"),
                skin = "shifutower:domadora_dragoes", minFloor = 5, maxFloor = 9,
                intro = listOf("Sinta o rugido dos dragões!", "Meus Pokémon nasceram para dominar os céus desta torre."),
                npcWins = listOf("Os dragões não perdoam.", "Volte quando tiver coragem de dragão."),
                npcLoses = listOf("Você domou até a minha fúria... incrível.", "Os céus da torre são seus. Por enquanto."),
            ),
            TrainerArchetype(
                id = "psiquica", title = "Médium Psíquica",
                names = listOf("Selene", "Aurora", "Luna", "Ágata", "Mística"),
                skin = "shifutower:psiquica", minFloor = 4, maxFloor = 9,
                intro = listOf("Eu já vi o resultado desta batalha nas estrelas...", "Sua mente é um livro aberto, {jogador}."),
                npcWins = listOf("Eu disse. As estrelas nunca mentem.", "Previsível..."),
                npcLoses = listOf("Esse futuro... eu não tinha visto.", "As estrelas mudaram de posição. Siga."),
            ),
            TrainerArchetype(
                id = "cavalheiro", title = "Cavalheiro",
                names = listOf("Sir Augusto", "Barão Edgar", "Conde Vicente", "Lorde Henrique"),
                skin = "shifutower:cavalheiro", minFloor = 6, maxFloor = 9,
                intro = listOf("Uma batalha entre cavalheiros, que tal?", "Permita-me apresentar meu esquadrão de elite."),
                npcWins = listOf("Foi um prazer. Tenha um bom dia.", "Elegância vence força."),
                npcLoses = listOf("Esplêndido! Simplesmente esplêndido.", "Tiro o chapéu para você, {jogador}."),
            ),
            TrainerArchetype(
                id = "mestre_shifu", title = "Mestre da Torre",
                names = listOf("Shifu"),
                skin = "shifutower:mestre_shifu", minFloor = 1, maxFloor = 999, boss = true,
                intro = listOf(
                    "Então você chegou ao topo... Eu sou Shifu, mestre desta torre.",
                    "Ninguém sobe {total} andares por sorte. Mostre-me o que te trouxe até aqui!",
                    "Meus lendários esperaram muito tempo por um desafiante digno.",
                ),
                npcWins = listOf(
                    "O topo da torre não é para qualquer um. Volte mais forte.",
                    "Você chegou longe, {jogador}... mas não longe o bastante.",
                ),
                npcLoses = listOf(
                    "...Incrível. Você é o verdadeiro campeão da Torre Shifu!",
                    "Há muito tempo ninguém me vencia. Leve este troféu com orgulho!",
                ),
            ),
        )
    }
}

class TrainerArchetype(
    @SerializedName("id") var id: String = "",
    @SerializedName("titulo") var title: String = "",
    @SerializedName("nomes") var names: List<String> = emptyList(),
    /**
     * "shifutower:<id>" usa uma skin do mod (assets/shifutower/textures/npcs),
     * "jogador:<Nick>" usa a skin de um jogador do Minecraft.
     */
    @SerializedName("skin") var skin: String = "",
    @SerializedName("andarMinimo") var minFloor: Int = 1,
    @SerializedName("andarMaximo") var maxFloor: Int = 999,
    @SerializedName("chefe") var boss: Boolean = false,
    @SerializedName("falasInicio") var intro: List<String> = emptyList(),
    @SerializedName("falasQuandoVence") var npcWins: List<String> = emptyList(),
    @SerializedName("falasQuandoPerde") var npcLoses: List<String> = emptyList(),
)

class ReceptionistConfig(
    @SerializedName("nome") var name: String = "Recepcionista Aline",
    @SerializedName("skin") var skin: String = "shifutower:recepcionista",
    @SerializedName("falasBoasVindas") var welcome: List<String> = listOf(
        "Bem-vindo à Torre Shifu, {jogador}! Pronto para subir?",
        "Olá, {jogador}! O Mestre Shifu espera no topo.",
        "A torre tem {total} andares. Quanto mais alto, mais Battle Points!",
    ),
    @SerializedName("falasSemTentativa") var noAttempts: List<String> = listOf(
        "Você já desafiou a torre hoje. Volte amanhã, {jogador}!",
        "Descanse seus Pokémon. Amanhã tem mais torre!",
    ),
)
