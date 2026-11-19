package com.filmemeu.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmemeu.app.data.Conta
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Perfil
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * "Quem vai assistir?" - a mesma tela do site, traco por traco.
 *
 * Antes esta tela era parecida de longe e diferente de perto: as carinhas
 * em fila no meio, seis desenhos proprios, dez cores. No site elas descem
 * numa coluna encostada a esquerda, sao setenta e oito figuras e sete
 * cores - e a capa ao fundo diz, no canto, que filme e aquele.
 *
 * Quem usa os dois nao devia ter de reaprender a tela ao trocar de
 * aparelho; e um perfil feito na web nao devia chegar aqui com outra cara.
 */

/* ----- as medidas do site ----- */

/** A carinha parada e em foco: 70 e 112 pontos, como no CSS. */
private val CARA_PARADA = 70
private val CARA_EM_FOCO = 112

/** A calha a esquerda onde o lapis mora, para ele nao cair fora da tela. */
private val CALHA_DO_LAPIS = 52.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaPerfis(aoEscolher: (Perfil) -> Unit, aoSair: () -> Unit) {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()

  var lista by remember { mutableStateOf<List<Perfil>>(emptyList()) }
  var recado by remember { mutableStateOf<String?>(null) }
  var carregando by remember { mutableStateOf(true) }

  var editando by remember { mutableStateOf<Perfil?>(null) }
  var criandoNovo by remember { mutableStateOf(false) }

  // As capas ao fundo.
  var capas by remember { mutableStateOf<List<Item>>(emptyList()) }
  var qualCapa by remember { mutableStateOf(0) }

  suspend fun recarregar() {
    carregando = true
    try {
      lista = Conta.garantirPerfis()
      recado = if (lista.isEmpty()) "Nao achamos nenhum perfil nesta conta." else null
    } catch (e: Exception) {
      // Engolir o erro aqui era o que escondia a falha: a tela recebia uma
      // lista vazia e nao tinha como saber que nada chegou.
      recado = "Nao deu para ler os perfis. Tente de novo."
    }
    carregando = false
  }

  LaunchedEffect(Unit) {
    recarregar()
    try {
      capas = Tmdb.lancamentos(Tipo.FILME).filter { it.fundo != null }.take(8)
    } catch (_: Exception) {
    }
  }

  // Duas camadas e so uma transicao de opacidade: o aparelho nao repinta a
  // tela por isso.
  LaunchedEffect(capas) {
    if (capas.isEmpty()) return@LaunchedEffect
    while (true) {
      delay(9000)
      qualCapa = (qualCapa + 1) % capas.size
    }
  }

  val emEdicao = editando != null || criandoNovo

  Box(Modifier.fillMaxSize().background(Cor.bg)) {

    if (capas.isNotEmpty()) {
      Crossfade(targetState = qualCapa, label = "capas") { qual ->
        AsyncImage(
          model = capas[qual].fundo,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
      }

      /*
       * O veu existe para o texto ser legivel sobre qualquer capa.
       *
       * Sao os dois mesmos degrades do site: um que escurece a esquerda,
       * onde ficam os perfis, e outro que fecha o alto e o pe. Um veu
       * chapado igual ao de antes apagava a capa inteira; assim ela
       * continua a vista do lado direito, que e a graca dela.
       */
      Box(
        Modifier.fillMaxSize().background(
          Brush.horizontalGradient(
            0.00f to Color(0xF5020617),
            0.34f to Color(0xDB020617),
            0.72f to Color(0x59020617),
            1.00f to Color(0x8C020617)
          )
        )
      )
      Box(
        Modifier.fillMaxSize().background(
          Brush.verticalGradient(
            0.00f to Color(0x8C020617),
            0.30f to Color(0x00020617),
            1.00f to Color(0x99020617)
          )
        )
      )
    }

    // Quem esta ao fundo. Sem isto a capa e so um enfeite bonito: a pessoa
    // ve o filme, gosta, e nao tem como saber o nome dele.
    if (capas.isNotEmpty()) {
      val capa = capas[qualCapa]
      Column(
        Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 40.dp, bottom = 40.dp)
          .widthIn(max = 420.dp),
        horizontalAlignment = Alignment.End
      ) {
        Text(
          when (capa.tipo) {
            Tipo.FILME -> "FILME"
            Tipo.SERIE -> "SÉRIE"
            Tipo.ANIME -> "ANIME"
          },
          color = Color(0xFF93C5FD),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 2.sp
        )
        Vao(4)
        Text(
          capa.titulo,
          color = Color.White,
          fontSize = 30.sp,
          fontWeight = FontWeight.Black,
          textAlign = TextAlign.End
        )
        if (capa.nota != null) {
          Vao(6)
          Text("★ " + String.format("%.1f", capa.nota), color = Color(0xFFE2E8F0), fontSize = 14.sp)
        }
      }
    }

    /*
     * A caixa encostada a esquerda, e nao no meio.
     *
     * E a mesma escolha do site: numa TV, a coluna da esquerda e a que o
     * controle alcanca primeiro, e deixa a capa respirando do outro lado.
     */
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(start = 32.dp + CALHA_DO_LAPIS, end = 32.dp, top = 24.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.Center
    ) {
      // A marca em cima, como quem diz de que casa e esta porta.
      NomeMovFlix(tamanho = 34)
      Vao(16)

      if (!emEdicao) {
        Text("Quem vai assistir?", color = Cor.text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Vao(22)

        if (recado != null) {
          Text(recado!!, color = Cor.danger, fontSize = 13.sp)
          Vao(14)
        }
        if (carregando) Rodinha()

        // Em coluna, e nao em fila: a lista desce, como na TV.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          lista.forEach { p ->
            UmPerfil(p, aoTocar = { aoEscolher(p) }, aoEditar = { editando = p })
          }

          // O "mais" so aparece enquanto cabe: tres comuns por conta, alem
          // do infantil. Mostrar um botao que vai dar erro nao ajuda
          // ninguem.
          if (lista.count { !it.infantil } < 3) {
            NovoPerfil { criandoNovo = true }
          }
        }

        Vao(20)
        BotaoTexto("Sair da conta", tamanho = 14) {
          Conta.sair(ctx)
          aoSair()
        }
      } else {
        EditorDePerfil(
          perfil = editando,
          aoSalvar = { nome, cor, figura ->
            escopo.launch {
              try {
                Conta.salvarPerfil(editando?.id, nome, cor, figura)
              } catch (_: Exception) {
                recado = "Nao deu para salvar o perfil."
              }
              editando = null
              criandoNovo = false
              recarregar()
            }
          },
          aoApagar = {
            val quem = editando
            if (quem != null) {
              escopo.launch {
                try {
                  Conta.apagarPerfil(quem.id)
                } catch (_: Exception) {
                  recado = "Nao deu para excluir o perfil."
                }
                editando = null
                recarregar()
              }
            }
          },
          aoCancelar = { editando = null; criandoNovo = false }
        )
      }
    }
  }
}

/*
 * Cada perfil e uma linha: a carinha e, quando em foco, o nome ao lado.
 *
 * Fora do foco a carinha e pequena e o nome nao aparece - sao tres ou
 * quatro perfis numa casa, e a imagem basta para reconhecer. Em foco ela
 * cresce e o nome entra, que e o que confirma a escolha antes do OK.
 */
@Composable
private fun UmPerfil(p: Perfil, aoTocar: () -> Unit, aoEditar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val lado by animateDpAsState(
    if (focado) CARA_EM_FOCO.dp else CARA_PARADA.dp,
    label = "cara"
  )

  Row(verticalAlignment = Alignment.CenterVertically) {
    /*
     * O lapis mora na calha a ESQUERDA da carinha, fora dela.
     *
     * Em cima do cartaz ele cobria o desenho e, no controle, o foco
     * passava por ele sem querer. Ao lado, e uma parada do caminho so
     * quando a pessoa vai para a esquerda - de proposito.
     *
     * Nunca no infantil: toda conta tem o dela, e apagar deixaria a casa
     * sem lugar para a crianca.
     */
    Box(Modifier.size(CALHA_DO_LAPIS), contentAlignment = Alignment.Center) {
      if (!p.infantil) {
        val caneta = remember { MutableInteractionSource() }
        val canetaEmFoco by caneta.collectIsFocusedAsState()
        Text(
          "✎",
          color = if (canetaEmFoco) Cor.text else Cor.muted,
          fontSize = 18.sp,
          modifier = Modifier
            .size(40.dp)
            .border(
              if (canetaEmFoco) 3.dp else 0.dp,
              if (canetaEmFoco) Cor.accent else Color.Transparent,
              CircleShape
            )
            .focusable(interactionSource = caneta)
            .clickable(interactionSource = caneta, indication = null) { aoEditar() }
            .padding(top = 8.dp),
          textAlign = TextAlign.Center
        )
      }
    }

    Box(
      Modifier
        .size(lado)
        .border(
          if (focado) 3.dp else 0.dp,
          if (focado) Color.White else Color.Transparent,
          RoundedCornerShape(8.dp)
        )
        .focusable(interactionSource = fonte)
        .clickable(interactionSource = fonte, indication = null) { aoTocar() },
      contentAlignment = Alignment.Center
    ) {
      Cara(p.figura, p.cor, (lado.value - (if (focado) 6 else 0)).toInt())

      /*
       * O selo do perfil infantil: uma etiqueta, nao uma barra.
       *
       * O nome pode ser o da crianca - Ana, Pedro -, e e isso que a casa
       * quer ver. Mas quem escolhe precisa saber, de relance, que aquele
       * perfil e o que so mostra coisa de crianca.
       */
      if (p.infantil) {
        Text(
          "INFANTIL",
          color = Color(0xFF0B1220),
          fontSize = (lado.value / 8f).sp,
          fontWeight = FontWeight.Black,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 6.dp)
            .background(corDeTexto(p.cor), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
        )
      }
    }

    // O nome so entra com o foco; parado, a carinha basta.
    VaoH(16)
    Text(
      p.nome,
      color = Color.White,
      fontSize = 21.sp,
      fontWeight = FontWeight.Black,
      modifier = Modifier.alpha(if (focado) 1f else 0f)
    )
  }
}

/** O lugar de criar outro: a mesma linha, com um mais no lugar da cara. */
@Composable
private fun NovoPerfil(aoTocar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val lado by animateDpAsState(
    if (focado) CARA_EM_FOCO.dp else CARA_PARADA.dp,
    label = "novo"
  )

  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(CALHA_DO_LAPIS))
    Box(
      Modifier
        .size(lado)
        .border(
          if (focado) 3.dp else 2.dp,
          if (focado) Color.White else Cor.lineSoft,
          RoundedCornerShape(8.dp)
        )
        .focusable(interactionSource = fonte)
        .clickable(interactionSource = fonte, indication = null) { aoTocar() },
      contentAlignment = Alignment.Center
    ) {
      Text("+", color = if (focado) Color.White else Cor.muted, fontSize = (lado.value / 2.4f).sp)
    }
    VaoH(16)
    Text(
      "Novo perfil",
      color = Color.White,
      fontSize = 21.sp,
      fontWeight = FontWeight.Black,
      modifier = Modifier.alpha(if (focado) 1f else 0f)
    )
  }
}

/**
 * Serve para criar e para editar.
 *
 * Os dois pedem as mesmas coisas, e duas telas quase iguais so dariam duas
 * chances de divergir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorDePerfil(
  perfil: Perfil?,
  aoSalvar: (String, String, String) -> Unit,
  aoApagar: () -> Unit,
  aoCancelar: () -> Unit
) {
  var nome by remember { mutableStateOf(perfil?.nome ?: "") }
  var cor by remember { mutableStateOf(perfil?.cor ?: CORES.first()) }
  var figura by remember { mutableStateOf(perfil?.figura ?: FIGURAS.first()) }

  Column(
    Modifier
      .widthIn(max = 620.dp)
      .border(1.dp, Cor.lineSoft, raio)
      .background(Cor.panel, raio)
      .padding(22.dp)
  ) {
    Text(
      if (perfil == null) "Criar perfil" else "Editar perfil",
      color = Cor.text, fontSize = 19.sp, fontWeight = FontWeight.Bold
    )
    Vao(16)

    Campo("Nome do perfil", nome, { if (it.length <= 24) nome = it }, Modifier.fillMaxWidth(),
      dica = "Como quer ser chamado")

    Vao(16)
    Text("Figura", color = Cor.muted, fontSize = 13.sp)
    Vao(8)
    /*
     * As setenta e oito figuras do site: dezessete rostos e sessenta e uma
     * caricaturas, na mesma ordem. A grade rola junto com a caixa, entao
     * nenhuma fica inalcancavel pelo controle.
     */
    FlowRow(
      Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      FIGURAS.forEach { f ->
        val escolhida = f == figura
        val fonte = remember { MutableInteractionSource() }
        val focada by fonte.collectIsFocusedAsState()
        Box(
          Modifier
            .border(
              2.dp,
              if (escolhida || focada) Color.White else Color.Transparent,
              RoundedCornerShape(10.dp)
            )
            .focusable(interactionSource = fonte)
            .clickable(interactionSource = fonte, indication = null) { figura = f }
            .padding(2.dp)
        ) {
          Cara(f, cor, 38)
        }
      }
    }

    Vao(16)
    Text("Cor", color = Cor.muted, fontSize = 13.sp)
    Vao(8)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      CORES.forEach { c ->
        val escolhida = c == cor
        val fonte = remember { MutableInteractionSource() }
        val focada by fonte.collectIsFocusedAsState()
        Box(
          Modifier
            .size(30.dp)
            .border(
              2.dp,
              if (escolhida || focada) Color.White else Color.Transparent,
              CircleShape
            )
            .padding(3.dp)
            .background(corDeTexto(c), CircleShape)
            .focusable(interactionSource = fonte)
            .clickable(interactionSource = fonte, indication = null) { cor = c }
        )
      }
    }

    Vao(20)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Botao(if (perfil == null) "Criar perfil" else "Salvar", jeito = Jeito.PRIMARIO) {
        if (nome.isNotBlank()) aoSalvar(nome.trim(), cor, figura)
      }
      Botao("Cancelar", jeito = Jeito.FANTASMA) { aoCancelar() }
      if (perfil != null && !perfil.infantil) {
        Botao("Excluir perfil", jeito = Jeito.PERIGO, pequeno = true) { aoApagar() }
      }
    }
  }
}
