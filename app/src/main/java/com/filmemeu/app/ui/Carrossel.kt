package com.filmemeu.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Carrossel de foco fixo (fixed-focus carousel, estilo Netflix na TV):
 * - A posicao da vaga em foco NAO muda: e sempre a primeira vaga a esquerda.
 * - O cartao em foco ocupa aquela vaga com escala fixa de 1.15 e formato
 *   widescreen (trailer automatico com audio + titulo + ★ nota + sinopse).
 * - Ao apertar para esquerda/direita, a fileira inteira desliza com animacao
 *   de 250 ms para que o cartao vizinho ocupe a vaga fixa.
 * - Nenhum cartao cresce ou encolhe no proprio lugar.
 */
@Composable
fun CarrosselFocoFixo(
  chaveLinha: String,
  itens: List<Item>,
  compacto: Boolean,
  focoLinha: FocusRequester,
  aoSubirDaLinha: () -> Unit,
  aoDescerDaLinha: () -> Unit,
  aoAbrir: (Item) -> Unit,
  aoPrecisarDeMais: (() -> Unit)? = null,
  etiquetaEpisodioDe: ((Item) -> String?)? = null,
  progressoDe: ((Item) -> Float?)? = null,
  aoRemover: ((Item) -> Unit)? = null
) {
  if (itens.isEmpty()) return

  val escopo = rememberCoroutineScope()
  val densidade = LocalDensity.current
  val fonteLinha = remember { MutableInteractionSource() }
  val linhaFocada by fonteLinha.collectIsFocusedAsState()

  val itensAtuais by rememberUpdatedState(itens)
  val aoPrecisarAtual by rememberUpdatedState(aoPrecisarDeMais)

  // Mantem a posicao atual na fileira mesmo quando mais paginas sao carregadas no fim.
  var indiceAtivo by remember(chaveLinha) { mutableIntStateOf(0) }
  if (indiceAtivo > itens.lastIndex) {
    indiceAtivo = itens.lastIndex
  }
  val deslocamentoPx = remember(chaveLinha) { Animatable(0f) }

  // Dispara pre-carregamento da proxima pagina bem antes de chegar no fim da fileira.
  LaunchedEffect(chaveLinha, indiceAtivo, itens.size) {
    val restantes = itens.size - 1 - indiceAtivo
    if (restantes <= 12) {
      aoPrecisarAtual?.invoke()
    }
  }

  // Dimensoes fixas (nenhum cartao anima tamanho no proprio lugar).
  val padH = if (compacto) 14.dp else 24.dp
  val larguraPoster = if (compacto) 94.dp else 124.dp
  val alturaPoster = (larguraPoster.value * 1.5f).dp
  val alturaRodape = if (compacto) 44.dp else 54.dp
  val alturaCard = alturaPoster + alturaRodape
  val espaco = if (compacto) 10.dp else 14.dp

  // Vaga fixa do cartao em foco: 100% travada em padH com escala constante 1.15f.
  val escalaFoco = 1.15f
  val larguraVagaBase = if (compacto) 212.dp else 296.dp
  val larguraVagaVisual = (larguraVagaBase.value * escalaFoco).dp
  val folgaVertical = ((alturaCard.value * (escalaFoco - 1f)) / 2f).dp + 6.dp
  val alturaFaixa = alturaCard + (folgaVertical * 2)

  val padHPx = with(densidade) { padH.toPx() }
  val larguraPosterPx = with(densidade) { larguraPoster.toPx() }
  val espacoPx = with(densidade) { espaco.toPx() }
  val larguraVagaVisualPx = with(densidade) { larguraVagaVisual.toPx() }
  val passoPx = larguraPosterPx + espacoPx

  fun deslizarPara(novoIndice: Int) {
    val lista = itensAtuais
    if (lista.isEmpty()) return
    val clamped = novoIndice.coerceIn(0, lista.lastIndex)
    if (clamped == indiceAtivo) return
    val direcao = clamped - indiceAtivo
    indiceAtivo = clamped
    escopo.launch {
      // Apenas a fileira de vizinhos desliza em 250 ms; a vaga em foco fica 100% parada em padH.
      val inicio = (direcao * passoPx).coerceIn(-passoPx * 2f, passoPx * 2f)
      deslocamentoPx.snapTo(inicio)
      deslocamentoPx.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
      )
    }
  }

  // Acumulador de arraste horizontal para gestos no celular/tablet.
  var arrasteAcumulado by remember { mutableFloatStateOf(0f) }

  BoxWithConstraints(
    Modifier
      .fillMaxWidth()
      .height(alturaFaixa)
      .clipToBounds()
      .focusRequester(focoLinha)
      .focusable(interactionSource = fonteLinha)
      .onPreviewKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (evento.key) {
          Key.DirectionRight -> {
            if (indiceAtivo + 1 < itensAtuais.size) deslizarPara(indiceAtivo + 1)
            true
          }
          Key.DirectionLeft -> {
            if (indiceAtivo > 0) deslizarPara(indiceAtivo - 1)
            true
          }
          Key.DirectionUp -> {
            aoSubirDaLinha()
            true
          }
          Key.DirectionDown -> {
            aoDescerDaLinha()
            true
          }
          Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
            val lista = itensAtuais
            if (lista.isNotEmpty()) aoAbrir(lista[indiceAtivo.coerceIn(0, lista.lastIndex)])
            true
          }
          else -> false
        }
      }
      .pointerInput(chaveLinha) {
        detectHorizontalDragGestures(
          onDragStart = { arrasteAcumulado = 0f },
          onHorizontalDrag = { change, dragAmount ->
            change.consume()
            arrasteAcumulado += dragAmount
            val limiar = passoPx * 0.45f
            if (arrasteAcumulado <= -limiar && indiceAtivo + 1 < itensAtuais.size) {
              arrasteAcumulado = 0f
              deslizarPara(indiceAtivo + 1)
            } else if (arrasteAcumulado >= limiar && indiceAtivo > 0) {
              arrasteAcumulado = 0f
              deslizarPara(indiceAtivo - 1)
            }
          }
        )
      }
  ) {
    val larguraViewportPx = with(densidade) { maxWidth.toPx() }
    val ativo = indiceAtivo.coerceIn(0, itens.lastIndex)

    // Quantos cartoes renderizar a direita para preencher toda a tela + folga do deslize.
    val larguraPrimeiroSlotPx = if (linhaFocada) larguraVagaVisualPx else larguraPosterPx
    val visiveisDireita = ceil(
      ((larguraViewportPx - padHPx - larguraPrimeiroSlotPx) / passoPx).coerceAtLeast(1f)
    ).toInt() + 3
    val inicioJanela = (ativo - 2).coerceAtLeast(0)
    val fimJanela = (ativo + visiveisDireita).coerceAtMost(itens.lastIndex)

    val deslocAtual = deslocamentoPx.value

    // 1) Fileira deslizante de vizinhos (e todos os cartazes quando a fileira nao esta focada).
    for (idx in inicioJanela..fimJanela) {
      val item = itens[idx]
      if (linhaFocada && idx == ativo) continue

      val xBasePx = when {
        idx == ativo -> padHPx
        idx < ativo -> padHPx + (idx - ativo) * passoPx
        else -> padHPx + larguraPrimeiroSlotPx + espacoPx + (idx - ativo - 1) * passoPx
      }
      val xFinalPx = (xBasePx + deslocAtual).roundToInt()

      key(item.tipo.chave, item.id, idx) {
        Box(
          Modifier
            .align(Alignment.CenterStart)
            .offset { IntOffset(xFinalPx, 0) }
            .zIndex(1f)
        ) {
          CartazSimples(
            item = item,
            largura = larguraPoster,
            alturaCapa = alturaPoster,
            alturaTotal = alturaCard,
            compacto = compacto,
            etiquetaEpisodio = etiquetaEpisodioDe?.invoke(item),
            progresso = progressoDe?.invoke(item),
            aoTocar = {
              if (linhaFocada) {
                deslizarPara(idx)
              } else {
                aoAbrir(item)
              }
            }
          )
        }
      }
    }

    // 2) Vaga fixa em foco: 100% travada em padH (nao desliza 1 pixel para o lado) com escala 1.15f.
    if (linhaFocada) {
      val itemAtivo = itens[ativo]
      Box(
        Modifier
          .align(Alignment.CenterStart)
          .offset { IntOffset(padHPx.roundToInt(), 0) }
          .zIndex(3f)
      ) {
        CartaoVagaFoco(
          item = itemAtivo,
          larguraBase = larguraVagaBase,
          alturaBase = alturaCard,
          escala = escalaFoco,
          compacto = compacto,
          etiquetaEpisodio = etiquetaEpisodioDe?.invoke(itemAtivo),
          progresso = progressoDe?.invoke(itemAtivo),
          aoTocar = { aoAbrir(itemAtivo) },
          aoRemover = aoRemover?.let { fn -> { fn(itemAtivo) } }
        )
      }
    }

    // Setas discretas nas bordas para clique com mouse/toque (nao focaveis no D-pad).
    if (ativo > 0) {
      SetaCarrossel(
        esquerda = true,
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 6.dp)
          .zIndex(4f)
      ) { deslizarPara(ativo - 1) }
    }
    if (ativo < itens.lastIndex) {
      SetaCarrossel(
        esquerda = false,
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 6.dp)
          .zIndex(4f)
      ) { deslizarPara(ativo + 1) }
    }
  }
}

/**
 * O cartao que ocupa a vaga fixa em foco (escala constante 1.15, estilo Netflix TV):
 * widescreen com trailer automatico com audio + titulo + ★ nota + sinopse e borda ciano.
 */
@Composable
private fun CartaoVagaFoco(
  item: Item,
  larguraBase: Dp,
  alturaBase: Dp,
  escala: Float,
  compacto: Boolean,
  etiquetaEpisodio: String?,
  progresso: Float?,
  aoTocar: () -> Unit,
  aoRemover: (() -> Unit)?
) {
  val forma = RoundedCornerShape(10.dp)

  var trailerKey by remember(item.tipo, item.id) { mutableStateOf<String?>(null) }
  var trailerPronto by remember(item.tipo, item.id) { mutableStateOf(false) }

  // Aguarda o deslize de 250 ms assentar antes de iniciar o trailer automatico com audio.
  LaunchedEffect(item.tipo, item.id) {
    trailerPronto = false
    trailerKey = null
    delay(400)
    trailerKey = try {
      Tmdb.trailer(item.tipo, item.id)
    } catch (_: Exception) {
      null
    }
  }

  val alphaVideo by animateFloatAsState(
    targetValue = if (trailerPronto) 1f else 0f,
    label = "vaga_trailer_alpha"
  )

  Column(
    Modifier
      .graphicsLayer {
        scaleX = escala
        scaleY = escala
        transformOrigin = TransformOrigin(0f, 0.5f)
      }
      .width(larguraBase)
      .height(alturaBase)
      .clip(forma)
      .background(Color(0xFF0E1629))
      .border(1.5.dp, Cor.accent, forma)
      .clickable { aoTocar() }
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .weight(1f)
        .background(Color(0xFF070C17))
    ) {
      AsyncImage(
        model = item.fundo ?: item.cartaz,
        contentDescription = item.titulo,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
      )

      val chave = trailerKey
      if (!chave.isNullOrBlank()) {
        key(chave) {
          TrailerEmbutido(
            videoKey = chave,
            visivel = trailerPronto,
            modifier = Modifier
              .fillMaxSize()
              .alpha(alphaVideo),
            aoMudarEstado = { pronto, _, _ ->
              trailerPronto = pronto
            }
          )
        }
      }

      if (progresso != null && progresso > 0f) {
        Box(
          Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(3.dp)
            .background(Color(0x880B1222))
        ) {
          Box(
            Modifier
              .fillMaxWidth(progresso.coerceIn(0f, 1f))
              .height(3.dp)
              .background(Cor.accent)
          )
        }
      }
    }

    Column(
      Modifier
        .fillMaxWidth()
        .background(Color(0xFF0E1629))
        .padding(
          horizontal = if (compacto) 7.dp else 10.dp,
          vertical = if (compacto) 5.dp else 7.dp
        )
    ) {
      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          item.titulo,
          color = Color.White,
          fontSize = if (compacto) 11.sp else 13.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (aoRemover != null) {
          Text(
            "remover",
            color = Cor.muted,
            fontSize = 9.5.sp,
            modifier = Modifier
              .padding(start = 6.dp)
              .clickable { aoRemover() }
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 1.dp)
      ) {
        if (item.nota != null) {
          Text(
            "★ ${item.nota}",
            color = Cor.warn,
            fontSize = if (compacto) 9.5.sp else 10.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
        if (!etiquetaEpisodio.isNullOrBlank()) {
          Text(
            etiquetaEpisodio,
            color = Cor.accent,
            fontSize = if (compacto) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      if (item.sinopse.isNotBlank()) {
        Text(
          item.sinopse,
          color = Color(0xFFCBD5E1),
          fontSize = if (compacto) 8.8.sp else 10.sp,
          lineHeight = if (compacto) 11.5.sp else 13.5.sp,
          maxLines = if (compacto) 2 else 3,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }
  }
}

/** Cartaz vertical padrao exibido nas demais posicoes do carrossel fixo. */
@Composable
private fun CartazSimples(
  item: Item,
  largura: Dp,
  alturaCapa: Dp,
  alturaTotal: Dp,
  compacto: Boolean,
  etiquetaEpisodio: String?,
  progresso: Float?,
  aoTocar: () -> Unit
) {
  val forma = RoundedCornerShape(10.dp)
  Column(
    Modifier
      .width(largura)
      .height(alturaTotal)
      .clickable { aoTocar() }
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(alturaCapa)
        .clip(forma)
        .background(Cor.bgSoft)
    ) {
      if (item.cartaz != null) {
        AsyncImage(
          model = item.cartaz,
          contentDescription = item.titulo,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
      } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("sem capa", color = Cor.muted, fontSize = 11.sp)
        }
      }

      Box(
        Modifier
          .align(Alignment.TopStart)
          .padding(6.dp)
          .clip(RoundedCornerShape(5.dp))
          .background(Cor.accentStrong)
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          when (item.tipo) {
            Tipo.FILME -> "FILME"
            Tipo.SERIE -> "SÉRIE"
            Tipo.ANIME -> "ANIME"
          },
          color = Color.White,
          fontSize = 8.5.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.4.sp
        )
      }

      if (!etiquetaEpisodio.isNullOrBlank()) {
        Box(
          Modifier
            .align(Alignment.BottomStart)
            .padding(start = 6.dp, bottom = if (progresso != null) 9.dp else 6.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xDD0B1222))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            etiquetaEpisodio,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      if (progresso != null && progresso > 0f) {
        Box(
          Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(3.5.dp)
            .background(Color(0x880B1222))
        ) {
          Box(
            Modifier
              .fillMaxWidth(progresso.coerceIn(0f, 1f))
              .height(3.5.dp)
              .background(Cor.accent)
          )
        }
      }
    }

    Vao(5)
    Text(
      item.titulo,
      color = Cor.text,
      fontSize = if (compacto) 11.sp else 12.5.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
    if (item.nota != null) {
      Spacer(Modifier.height(2.dp))
      Text(
        "★ ${item.nota}",
        color = Cor.warn,
        fontSize = if (compacto) 10.5.sp else 11.5.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun SetaCarrossel(
  esquerda: Boolean,
  modifier: Modifier = Modifier,
  aoTocar: () -> Unit
) {
  Box(
    modifier
      .size(30.dp)
      .clip(CircleShape)
      .background(Color(0xB80B1222))
      .border(1.dp, Color(0x33FFFFFF), CircleShape)
      .clickable { aoTocar() },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = if (esquerda) {
        Icons.AutoMirrored.Rounded.KeyboardArrowLeft
      } else {
        Icons.AutoMirrored.Rounded.KeyboardArrowRight
      },
      contentDescription = if (esquerda) "Anterior" else "Próximo",
      tint = Color.White,
      modifier = Modifier.size(18.dp)
    )
  }
}

/**
 * Cartaz padrao para grades (Busca, Categorias/Ver todos e Relacionados na pagina de detalhe).
 */
@Composable
fun Cartaz(
  item: Item,
  largura: Int = 136,
  etiquetaEpisodio: String? = null,
  progresso: Float? = null,
  modifier: Modifier = Modifier,
  aoMudarFoco: ((Boolean) -> Unit)? = null,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val forma = RoundedCornerShape(10.dp)

  LaunchedEffect(focado) {
    aoMudarFoco?.invoke(focado)
  }

  Column(
    modifier
      .width(largura.dp)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f)
        .clip(forma)
        .border(
          width = if (focado) 2.5.dp else 0.dp,
          color = if (focado) Cor.accent else Color.Transparent,
          shape = forma
        )
        .background(Cor.bgSoft)
    ) {
      if (item.cartaz != null) {
        AsyncImage(
          model = item.cartaz,
          contentDescription = item.titulo,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
      } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("sem capa", color = Cor.muted, fontSize = 11.sp)
        }
      }

      Box(
        Modifier
          .align(Alignment.TopStart)
          .padding(7.dp)
          .clip(RoundedCornerShape(5.dp))
          .background(Cor.accentStrong)
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Text(
          when (item.tipo) {
            Tipo.FILME -> "FILME"
            Tipo.SERIE -> "SÉRIE"
            Tipo.ANIME -> "ANIME"
          },
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.4.sp
        )
      }

      if (!etiquetaEpisodio.isNullOrBlank()) {
        Box(
          Modifier
            .align(Alignment.BottomStart)
            .padding(start = 7.dp, bottom = if (progresso != null) 10.dp else 7.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xDD0B1222))
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            etiquetaEpisodio,
            color = Color.White,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      if (focado) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color(0x44000000)),
          contentAlignment = Alignment.Center
        ) {
          Row(
            Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(Cor.accentStrong)
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Icon(
              imageVector = Icons.Rounded.PlayArrow,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Text(
              "Abrir",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      if (progresso != null && progresso > 0f) {
        Box(
          Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(3.5.dp)
            .background(Color(0x880B1222))
        ) {
          Box(
            Modifier
              .fillMaxWidth(progresso.coerceIn(0f, 1f))
              .height(3.5.dp)
              .background(Cor.accent)
          )
        }
      }
    }

    Vao(6)
    Text(
      item.titulo,
      color = Cor.text,
      fontSize = if (largura < 120) 12.sp else 13.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      lineHeight = if (largura < 120) 15.sp else 17.sp
    )
    if (item.nota != null || item.ano != null) {
      Spacer(Modifier.height(2.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (item.nota != null) {
          Text(
            "★ ${item.nota}",
            color = Cor.warn,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
          )
        }
        if (item.ano != null) {
          Text(
            "${item.ano}",
            color = Cor.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
  }
}
