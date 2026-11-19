package com.filmemeu.app.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/**
 * As figuras de perfil.
 *
 * Desenhos nossos, no mesmo traco da carinha do menu do site: rostos
 * geometricos simples de proposito. Num quadrado de cem pixels, visto do
 * sofa, o que a pessoa reconhece e a cor e a forma grossa; detalhe fino
 * some. E sendo nossos, mudam de estilo junto com o resto do aplicativo,
 * sem depender de imagem em servidor nenhum.
 */
val FIGURAS = listOf("sorriso", "serio", "sono", "espanto", "oculos", "bigode")

/** As cores que o site oferece na edicao do perfil. */
val CORES = listOf(
  "#38bdf8", "#f59e0b", "#34d399", "#a78bfa", "#f87171", "#fbbf24",
  "#22d3ee", "#fb7185", "#4ade80", "#60a5fa"
)

fun corDeTexto(hex: String): Color = try {
  Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
  Cor.accent
}

/**
 * Um rosto desenhado na hora.
 *
 * Tudo em cima do quadrado da cor do perfil: dois olhos e uma boca, que
 * mudam conforme a figura escolhida.
 */
@Composable
fun Cara(figura: String, cor: String, tamanho: Int, modifier: Modifier = Modifier) {
  val fundo = corDeTexto(cor)
  val traco = Cor.bg

  Box(
    modifier
      .size(tamanho.dp)
      .background(fundo, RoundedCornerShape((tamanho / 4.5f).dp))
      .drawBehind {
        val l = size.width
        val olhoY = l * 0.41f
        val olhoR = l * 0.072f
        val esq = Offset(l * 0.365f, olhoY)
        val dir = Offset(l * 0.635f, olhoY)
        val grosso = l * 0.075f

        when (figura) {
          "sono" -> {
            // Olhos fechados: dois tracos.
            drawLine(traco, Offset(esq.x - olhoR, olhoY), Offset(esq.x + olhoR, olhoY),
              grosso, StrokeCap.Round)
            drawLine(traco, Offset(dir.x - olhoR, olhoY), Offset(dir.x + olhoR, olhoY),
              grosso, StrokeCap.Round)
          }
          "oculos" -> {
            drawCircle(traco, olhoR * 1.9f, esq, style = Stroke(grosso * 0.7f))
            drawCircle(traco, olhoR * 1.9f, dir, style = Stroke(grosso * 0.7f))
            drawLine(traco, Offset(esq.x + olhoR * 1.9f, olhoY),
              Offset(dir.x - olhoR * 1.9f, olhoY), grosso * 0.7f)
          }
          else -> {
            drawCircle(traco, olhoR, esq)
            drawCircle(traco, olhoR, dir)
          }
        }

        // A boca.
        when (figura) {
          "serio" -> drawLine(
            traco, Offset(l * 0.35f, l * 0.66f), Offset(l * 0.65f, l * 0.66f),
            grosso, StrokeCap.Round
          )
          "espanto" -> drawCircle(traco, l * 0.085f, Offset(l * 0.5f, l * 0.66f))
          "bigode" -> {
            drawLine(
              traco, Offset(l * 0.33f, l * 0.60f), Offset(l * 0.67f, l * 0.60f),
              grosso * 1.5f, StrokeCap.Round
            )
            drawArc(
              traco, 20f, 140f, false,
              Offset(l * 0.33f, l * 0.56f), Size(l * 0.34f, l * 0.26f),
              style = Stroke(grosso, cap = StrokeCap.Round)
            )
          }
          else -> drawArc(
            traco, 20f, 140f, false,
            Offset(l * 0.32f, l * 0.46f), Size(l * 0.36f, l * 0.30f),
            style = Stroke(grosso, cap = StrokeCap.Round)
          )
        }
      }
  )
}

/**
 * "Quem vai assistir?"
 *
 * Entre a conta e o catalogo: a conta e de quem paga, o perfil e de quem
 * assiste. Numa casa com mais de uma pessoa, e o perfil que separa o que
 * cada uma deixou pela metade.
 *
 * Ao fundo, as capas do catalogo se revezando, como no site.
 */
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

  Box(Modifier.fillMaxSize().background(Cor.bg)) {

    if (capas.isNotEmpty()) {
      Crossfade(targetState = qualCapa, label = "capas") { qual ->
        AsyncImage(
          model = capas[qual].fundo,
          contentDescription = null,
          modifier = Modifier.fillMaxSize().alpha(0.35f),
          contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
      }
      // O veu, para o texto continuar legivel por cima da capa.
      Box(Modifier.fillMaxSize().background(Color(0xCC0F172A)))
    }

    // Quem esta ao fundo. Sem isto a capa e so um enfeite bonito: a pessoa
    // ve o filme, gosta, e nao tem como saber o nome dele.
    if (capas.isNotEmpty()) {
      val capa = capas[qualCapa]
      Column(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
        Text(
          if (capa.tipo == Tipo.FILME) "FILME" else "SERIE",
          color = Cor.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        Text(capa.titulo, color = Cor.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (capa.nota != null) {
          Text("Nota " + capa.nota, color = Cor.warn, fontSize = 12.sp)
        }
      }
    }

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      NomeMovFlix(tamanho = 22)
      Vao(10)

      val emEdicao = editando != null || criandoNovo

      if (!emEdicao) {
        Text("Quem vai assistir?", color = Cor.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Vao(26)

        if (recado != null) {
          Text(recado!!, color = Cor.danger, fontSize = 13.sp)
          Vao(14)
        }
        if (carregando) Rodinha()

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(22.dp),
          verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
          lista.forEach { p ->
            UmPerfil(p, aoTocar = { aoEscolher(p) }, aoEditar = { editando = p })
          }
          // O lugar de criar outro, como no site.
          if (lista.size < 5) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              val fonte = remember { MutableInteractionSource() }
              val focado by fonte.collectIsFocusedAsState()
              Box(
                Modifier
                  .size(100.dp)
                  .border(if (focado) 3.dp else 2.dp, if (focado) Cor.text else Cor.lineSoft,
                    RoundedCornerShape(22.dp))
                  .focusable(interactionSource = fonte)
                  .clickable(interactionSource = fonte, indication = null) { criandoNovo = true },
                contentAlignment = Alignment.Center
              ) {
                Text("+", color = Cor.muted, fontSize = 40.sp)
              }
              Vao(8)
              Text("Novo perfil", color = if (focado) Cor.text else Cor.muted, fontSize = 14.sp)
            }
          }
        }

        Vao(30)
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

@Composable
private fun UmPerfil(p: Perfil, aoTocar: () -> Unit, aoEditar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier
        .size(if (focado) 108.dp else 100.dp)
        .border(
          if (focado) 3.dp else 0.dp,
          if (focado) Cor.text else Color.Transparent,
          RoundedCornerShape(24.dp)
        )
        .focusable(interactionSource = fonte)
        .clickable(interactionSource = fonte, indication = null) { aoTocar() },
      contentAlignment = Alignment.Center
    ) {
      Cara(p.figura, p.cor, if (focado) 100 else 94)
    }
    Vao(8)
    Text(
      p.nome,
      color = if (focado) Cor.text else Cor.muted,
      fontSize = 15.sp,
      fontWeight = if (focado) FontWeight.SemiBold else FontWeight.Normal
    )
    // Nunca no infantil: toda conta tem o dela, e apagar deixaria a casa
    // sem lugar para a crianca.
    if (!p.infantil) {
      BotaoTexto("editar", tamanho = 11) { aoEditar() }
    }
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
      .widthIn(max = 420.dp)
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
    Text("Figura", color = Cor.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Vao(8)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FIGURAS.forEach { f ->
        val escolhida = f == figura
        Box(
          Modifier
            .border(2.dp, if (escolhida) Cor.text else Color.Transparent, RoundedCornerShape(12.dp))
            .focusable()
            .clickable { figura = f }
            .padding(2.dp)
        ) {
          Cara(f, cor, 42)
        }
      }
    }

    Vao(16)
    Text("Cor", color = Cor.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Vao(8)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CORES.forEach { c ->
        val escolhida = c == cor
        Box(
          Modifier
            .size(34.dp)
            .border(2.dp, if (escolhida) Cor.text else Color.Transparent, CircleShape)
            .padding(3.dp)
            .background(corDeTexto(c), CircleShape)
            .focusable()
            .clickable { cor = c }
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
