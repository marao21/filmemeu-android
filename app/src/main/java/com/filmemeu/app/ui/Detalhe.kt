package com.filmemeu.app.ui

import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.filmemeu.app.data.Conta
import com.filmemeu.app.data.Fonte
import com.filmemeu.app.data.Fontes
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Item
import com.filmemeu.app.data.Temporada
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.data.Tmdb
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A pagina de informacoes do titulo:
 * - Mesmo cabecalho superior do MovFlix (avatar, lupa, abas, Minha MovFlix)
 * - Palco widescreen limpo com trailer automatico com audio (sem botoes flutuantes)
 * - Foco inicial sempre no botao "▶ Assistir" (ou "▶ Continuar assistindo")
 * - Apertar para baixo desce suavemente para a fileira "Relacionados"
 */
@Composable
fun TelaDetalhe(
  item: Item,
  abaAtual: Int,
  aoTrocarAba: (Int) -> Unit,
  aoAbrirBusca: () -> Unit,
  aoTrocarPerfil: () -> Unit,
  aoSair: () -> Unit,
  aoVoltar: () -> Unit,
  aoAssistir: (Fonte, Long) -> Unit,
  aoAbrirOutro: (Item) -> Unit
) {
  val ctx = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val infantil = Conta.perfil?.infantil == true

  var temporadas by remember(item.id) { mutableStateOf<List<Temporada>>(emptyList()) }
  var temporada by remember(item.id) { mutableStateOf(1) }
  var episodio by remember(item.id) { mutableStateOf(1) }
  var parecidos by remember(item.id) { mutableStateOf<List<Item>>(emptyList()) }
  var trailerKey by remember(item.id) { mutableStateOf<String?>(null) }
  var trailerPronto by remember(item.id) { mutableStateOf(false) }
  var trailerWebView by remember(item.id) { mutableStateOf<WebView?>(null) }
  var naMinha by remember(item.id) { mutableStateOf(Guardados.estaNaMinha(ctx, item.tipo, item.id)) }

  var procurandoFonte by remember(item.id) { mutableStateOf(false) }
  var recadoDaBusca by remember(item.id) { mutableStateOf("") }
  val escopo = rememberCoroutineScope()

  val estadoRolagem = rememberScrollState()
  val focoAssistir = remember(item.id) { FocusRequester() }
  val focoPrimeiroRelacionado = remember(item.id) { FocusRequester() }

  // Ao abrir qualquer titulo, posiciona no topo e coloca o cursor direto no botao Assistir.
  LaunchedEffect(item.id) {
    estadoRolagem.scrollTo(0)
    var focou = false
    repeat(6) {
      if (!focou) {
        delay(24)
        focou = runCatching { focoAssistir.requestFocus(); true }.getOrDefault(false)
      }
    }
  }

  LaunchedEffect(item.id) {
    if (item.tipo.ehSerie) {
      temporadas = try {
        Tmdb.temporadas(item.id)
      } catch (_: Exception) {
        emptyList()
      }
    }
    trailerKey = try {
      Tmdb.trailer(item.tipo, item.id)
    } catch (_: Exception) {
      null
    }
    parecidos = try {
      Tmdb.parecidos(item.tipo, item.id, infantil)
    } catch (_: Exception) {
      emptyList()
    }
  }

  DisposableEffect(lifecycleOwner, trailerWebView) {
    val obs = LifecycleEventObserver { _, evento ->
      val wv = trailerWebView ?: return@LifecycleEventObserver
      runCatching {
        if (evento == Lifecycle.Event.ON_PAUSE || evento == Lifecycle.Event.ON_STOP) {
          wv.evaluateJavascript("if(window.pausarTrailer) window.pausarTrailer();", null)
          wv.onPause()
        } else if (evento == Lifecycle.Event.ON_RESUME) {
          wv.onResume()
          wv.evaluateJavascript("if(window.tocarComSom) window.tocarComSom();", null)
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(obs)
    }
  }

  val onde = Guardados.posicaoDe(ctx, item.tipo, item.id, temporada, episodio)

  fun pausarTrailerAntesDeSair() {
    trailerWebView?.let { wv ->
      runCatching {
        wv.evaluateJavascript("if(window.pausarTrailer) window.pausarTrailer();", null)
        wv.onPause()
      }
    }
  }

  fun descerParaRelacionados() {
    if (parecidos.isEmpty()) return
    escopo.launch {
      estadoRolagem.animateScrollTo(estadoRolagem.maxValue)
      var focou = false
      repeat(5) {
        if (!focou) {
          focou = runCatching { focoPrimeiroRelacionado.requestFocus(); true }.getOrDefault(false)
          if (!focou) delay(24)
        }
      }
    }
  }

  fun subirParaAssistir() {
    escopo.launch {
      estadoRolagem.animateScrollTo(0)
      var focou = false
      repeat(5) {
        if (!focou) {
          focou = runCatching { focoAssistir.requestFocus(); true }.getOrDefault(false)
          if (!focou) delay(24)
        }
      }
    }
  }

  fun assistir(doComeco: Boolean) {
    if (procurandoFonte) return
    procurandoFonte = true
    recadoDaBusca = ""

    escopo.launch {
      val guardadaAgora = Guardados.fonte(ctx, item.tipo, item.id, temporada, episodio)
      if (guardadaAgora != null) {
        val ver = Fontes.verificar(guardadaAgora.url, guardadaAgora.referer)
        if (ver is Fontes.ResultadoVerificacao.Valida) {
          procurandoFonte = false
          val valida = guardadaAgora.copy(url = ver.urlFinal, referer = ver.referer)
          if (valida.url != guardadaAgora.url) Guardados.guardarFonte(ctx, valida)
          pausarTrailerAntesDeSair()
          aoAssistir(valida, if (doComeco) 0L else onde)
          return@launch
        }
        Guardados.apagarFonte(ctx, item.tipo, item.id, temporada, episodio)
      }

      val achada = try {
        Fontes.procurar(item.tipo, item.id, temporada, episodio)
      } catch (_: Exception) {
        null
      }
      procurandoFonte = false

      if (achada != null) {
        Guardados.guardarFonte(ctx, achada)
        pausarTrailerAntesDeSair()
        aoAssistir(achada, if (doComeco) 0L else onde)
      } else {
        recadoDaBusca = "Nenhum servidor abriu este título agora."
      }
    }
  }

  val alphaTrailer by animateFloatAsState(
    targetValue = if (trailerPronto) 1f else 0f,
    label = "trailer_alpha"
  )

  BoxWithConstraints(Modifier.fillMaxSize().background(Cor.bg)) {
    val compacto = maxHeight < 460.dp || maxWidth < 740.dp
    val alturaHeroMin = if (compacto) 275.dp else 410.dp
    val larguraCard = if (compacto) 110 else 138

    Column(Modifier.fillMaxSize().background(Cor.bg)) {
      CabecalhoMovFlix(
        abaSelecionada = abaAtual,
        buscaAberta = false,
        compacto = compacto,
        aoFocarCabecalho = {
          escopo.launch { estadoRolagem.animateScrollTo(0) }
        },
        aoDescerDoCabecalho = {
          subirParaAssistir()
          true
        },
        aoAbrirBusca = {
          pausarTrailerAntesDeSair()
          aoAbrirBusca()
        },
        aoTrocarAba = { novaAba ->
          pausarTrailerAntesDeSair()
          aoTrocarAba(novaAba)
        },
        aoTrocarPerfil = {
          pausarTrailerAntesDeSair()
          aoTrocarPerfil()
        },
        aoSair = {
          pausarTrailerAntesDeSair()
          aoSair()
        }
      )

      Column(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(estadoRolagem)
      ) {
        Box(
          Modifier
            .fillMaxWidth()
            .heightIn(min = alturaHeroMin)
            .background(Color(0xFF070C17))
        ) {
          AsyncImage(
            model = item.fundo ?: item.cartaz,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
          )

          val chave = trailerKey
          if (!chave.isNullOrBlank()) {
            key(chave) {
              TrailerEmbutido(
                videoKey = chave,
                visivel = trailerPronto,
                modifier = Modifier
                  .matchParentSize()
                  .alpha(alphaTrailer),
                aoCriarWebView = { trailerWebView = it },
                aoMudarEstado = { pronto, _, _ ->
                  trailerPronto = pronto
                }
              )
            }
          }

          Box(
            Modifier
              .matchParentSize()
              .background(
                Brush.horizontalGradient(
                  listOf(Color(0xF00B1222), Color(0xB80B1222), Color(0x1F0B1222))
                )
              )
          )
          Box(
            Modifier
              .matchParentSize()
              .background(
                Brush.verticalGradient(
                  listOf(Color(0x220B1222), Color.Transparent, Cor.bg)
                )
              )
          )

          Column(
            Modifier
              .align(Alignment.CenterStart)
              .padding(
                horizontal = if (compacto) 16.dp else 30.dp,
                vertical = if (compacto) 14.dp else 24.dp
              )
          ) {
            BotaoVoltarCatalogo(compacto = compacto) {
              pausarTrailerAntesDeSair()
              aoVoltar()
            }
            Vao(if (compacto) 10 else 14)

            Text(
              item.titulo,
              color = Cor.text,
              fontSize = if (compacto) 24.sp else 34.sp,
              fontWeight = FontWeight.Black,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth(if (compacto) 0.65f else 0.48f)
            )
            Vao(6)

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (item.nota != null) {
                Text(
                  "★ ${item.nota}",
                  color = Cor.warn,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold
                )
                Text("·", color = Cor.muted, fontSize = 13.sp)
              }
              if (item.ano != null) {
                Text("${item.ano}", color = Cor.muted, fontSize = 13.sp)
                Text("·", color = Cor.muted, fontSize = 13.sp)
              }
              Text(
                when (item.tipo) {
                  Tipo.FILME -> "Filme"
                  Tipo.SERIE -> "Série"
                  Tipo.ANIME -> "Animação"
                },
                color = Cor.muted,
                fontSize = 13.sp
              )
            }

            if (item.sinopse.isNotEmpty()) {
              Vao(if (compacto) 8 else 12)
              Text(
                item.sinopse,
                color = Color(0xFFCBD5E1),
                fontSize = if (compacto) 12.5.sp else 14.sp,
                lineHeight = if (compacto) 18.sp else 20.sp,
                maxLines = if (compacto) 3 else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(if (compacto) 0.58f else 0.42f)
              )
            }

            if (item.tipo.ehSerie && temporadas.isNotEmpty()) {
              Vao(12)
              Text("Temporada", color = Cor.muted, fontSize = 12.sp)
              Vao(6)
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(if (compacto) 0.65f else 0.48f)
              ) {
                items(temporadas) { t ->
                  Ficha("T" + t.numero, t.numero == temporada) {
                    temporada = t.numero
                    episodio = 1
                  }
                }
              }

              val quantos = temporadas.firstOrNull { it.numero == temporada }?.episodios ?: 1
              Vao(8)
              Text("Episódio", color = Cor.muted, fontSize = 12.sp)
              Vao(6)
              LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(if (compacto) 0.65f else 0.48f)
              ) {
                items((1..quantos).toList()) { e ->
                  Ficha("" + e, e == episodio) { episodio = e }
                }
              }
            }

            Vao(if (compacto) 12 else 18)
            Row(
              Modifier.onPreviewKeyEvent { evento ->
                if (evento.type == KeyEventType.KeyDown &&
                  evento.key == Key.DirectionDown &&
                  parecidos.isNotEmpty()
                ) {
                  descerParaRelacionados()
                  true
                } else {
                  false
                }
              },
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Botao(
                when {
                  procurandoFonte -> "Procurando o filme…"
                  onde > 0 -> "▶ Continuar assistindo"
                  else -> "▶ Assistir"
                },
                modifier = Modifier
                  .testTag("detail_watch_button")
                  .focusRequester(focoAssistir),
                jeito = Jeito.PRIMARIO,
                pequeno = compacto
              ) { assistir(false) }

              if (onde > 0) {
                Botao("Assistir do início", pequeno = compacto) { assistir(true) }
              }

              Botao(
                if (naMinha) "✓ Na minha lista" else "+ Minha lista",
                pequeno = true
              ) {
                naMinha = Guardados.virarNaMinha(ctx, item)
              }
            }

            if (recadoDaBusca.isNotBlank()) {
              Vao(10)
              Text(recadoDaBusca, color = Cor.danger, fontSize = 13.sp)
            }
          }
        }

        if (parecidos.isNotEmpty()) {
          Column(Modifier.padding(top = if (compacto) 14.dp else 20.dp, bottom = 30.dp)) {
            Text(
              "Relacionados",
              color = Cor.text,
              fontSize = if (compacto) 15.sp else 18.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(
                start = if (compacto) 14.dp else 24.dp,
                bottom = if (compacto) 8.dp else 12.dp
              )
            )
            LazyRow(
              contentPadding = PaddingValues(horizontal = if (compacto) 14.dp else 24.dp),
              horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 14.dp),
              modifier = Modifier.onPreviewKeyEvent { evento ->
                if (evento.type == KeyEventType.KeyDown && evento.key == Key.DirectionUp) {
                  subirParaAssistir()
                  true
                } else {
                  false
                }
              }
            ) {
              itemsIndexed(parecidos) { idx, p ->
                Cartaz(
                  item = p,
                  largura = larguraCard,
                  modifier = if (idx == 0) Modifier.focusRequester(focoPrimeiroRelacionado) else Modifier
                ) {
                  pausarTrailerAntesDeSair()
                  aoAbrirOutro(p)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BotaoVoltarCatalogo(compacto: Boolean, aoTocar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val forma = RoundedCornerShape(8.dp)
  Box(
    Modifier
      .testTag("detail_back_button")
      .clip(forma)
      .background(if (focado) Cor.line else Color(0xAA0F172A))
      .border(1.dp, if (focado) Cor.accent else Cor.lineSoft, forma)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(
        horizontal = if (compacto) 10.dp else 14.dp,
        vertical = if (compacto) 6.dp else 8.dp
      )
  ) {
    Text(
      "← Voltar ao catálogo",
      color = if (focado) Cor.text else Color(0xFFCBD5E1),
      fontSize = if (compacto) 12.sp else 13.sp,
      fontWeight = FontWeight.Medium
    )
  }
}
