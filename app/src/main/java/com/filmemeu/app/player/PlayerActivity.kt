package com.filmemeu.app.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.filmemeu.app.data.Fontes
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Marca
import com.filmemeu.app.data.Progresso
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.net.Http
import com.filmemeu.app.ui.Cor
import com.filmemeu.app.ui.Rodinha
import com.filmemeu.app.ui.Vao
import com.filmemeu.app.ui.VaoH
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

/**
 * O nosso player.
 *
 * No site era um video do navegador com hls.js por cima. Aqui e o ExoPlayer
 * do proprio Android: HLS e MP4 sem biblioteca de fora, decodificacao do
 * aparelho, e os controles desenhados por nos — que e o que faz o controle
 * remoto da TV chegar neles.
 */
class PlayerActivity : ComponentActivity() {

  companion object {
    private const val URL = "url"
    private const val REFERER = "referer"
    private const val TITULO = "titulo"
    private const val CARTAZ = "cartaz"
    private const val TIPO = "tipo"
    private const val ID = "id"
    private const val TEMPORADA = "temporada"
    private const val EPISODIO = "episodio"
    private const val POSICAO = "posicao"

    fun abrir(
      ctx: Context,
      url: String,
      referer: String?,
      titulo: String,
      cartaz: String?,
      tipo: Tipo,
      id: Int,
      temporada: Int,
      episodio: Int,
      posicao: Long
    ) {
      val i = Intent(ctx, PlayerActivity::class.java)
      i.putExtra(URL, url)
      i.putExtra(REFERER, referer)
      i.putExtra(TITULO, titulo)
      i.putExtra(CARTAZ, cartaz)
      i.putExtra(TIPO, tipo.chave)
      i.putExtra(ID, id)
      i.putExtra(TEMPORADA, temporada)
      i.putExtra(EPISODIO, episodio)
      i.putExtra(POSICAO, posicao)
      ctx.startActivity(i)
    }
  }

  override fun onCreate(estado: Bundle?) {
    super.onCreate(estado)
    // Ninguem quer a tela apagando no meio do filme.
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val url = intent.getStringExtra(URL) ?: run { finish(); return }
    val referer = intent.getStringExtra(REFERER)
    val titulo = intent.getStringExtra(TITULO) ?: ""
    val cartaz = intent.getStringExtra(CARTAZ)
    val tipo = Tipo.de(intent.getStringExtra(TIPO))
    val id = intent.getIntExtra(ID, 0)
    val temporada = intent.getIntExtra(TEMPORADA, 1)
    val episodio = intent.getIntExtra(EPISODIO, 1)
    val posicao = intent.getLongExtra(POSICAO, 0L)

    setContent {
      Palco(url, referer, titulo, cartaz, tipo, id, temporada, episodio, posicao) { finish() }
    }
  }
}

/**
 * O palco: o video, a abertura da marca e os controles.
 *
 * A barra e os controles somem sozinhos depois de um tempo quieto, como no
 * site, e voltam a qualquer toque ou tecla do controle.
 */
@OptIn(UnstableApi::class)
@Composable
private fun Palco(
  url: String,
  referer: String?,
  titulo: String,
  cartaz: String?,
  tipo: Tipo,
  id: Int,
  temporada: Int,
  episodio: Int,
  posicao: Long,
  aoSair: () -> Unit
) {
  val ctx = LocalContext.current

  var urlAtual by remember { mutableStateOf(url) }
  var refererAtual by remember { mutableStateOf(referer) }
  var tentativa by remember { mutableStateOf(0) }
  var tentouRenovar by remember { mutableStateOf(false) }
  val refererRef = remember { AtomicReference(referer) }

  var tocando by remember { mutableStateOf(true) }
  var onde by remember { mutableStateOf(posicao) }
  var quanto by remember { mutableStateOf(0L) }
  var guardado by remember { mutableStateOf(0L) }
  var mudo by remember { mutableStateOf(false) }
  var volume by remember { mutableStateOf(1f) }
  var qualidade by remember { mutableStateOf("") }

  // Procurar o proximo episodio conversa com a origem e leva segundos.
  // Sem este aviso, o botao pareceria morto no primeiro toque.
  var procurandoProximo by remember { mutableStateOf(false) }
  val escopo = rememberCoroutineScope()
  var carregando by remember { mutableStateOf(true) }
  var falha by remember { mutableStateOf<String?>(null) }
  var quieto by remember { mutableStateOf(false) }
  var abertura by remember { mutableStateOf(true) }
  var ultimoToque by remember { mutableStateOf(System.currentTimeMillis()) }

  /**
   * ExoPlayer apoiado no mesmo OkHttpClient que valida as fontes, com
   * AudioSink tolerante a pequenos saltos de timestamp (>200ms) comuns em
   * segmentos HLS/MPEG-TS sem #EXT-X-DISCONTINUITY.
   */
  val player = remember {
    val fabricaHttp = OkHttpDataSource.Factory(Http.cliente) {
      Http.cabecalhosVideo(refererRef.get())
    }
    val renderers = object : DefaultRenderersFactory(ctx) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
      ): AudioSink? {
        val base = super.buildAudioSink(
          context,
          enableFloatOutput,
          enableAudioTrackPlaybackParams
        ) ?: return null
        return TolerantAudioSink(base)
      }
    }.setEnableDecoderFallback(true)

    val audioAttrs = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
      .build()

    ExoPlayer.Builder(ctx, renderers)
      .setMediaSourceFactory(DefaultMediaSourceFactory(fabricaHttp))
      .setAudioAttributes(audioAttrs, /* handleAudioFocus = */ true)
      .setHandleAudioBecomingNoisy(true)
      .build()
  }

  fun prepararNoPlayer(endereco: String, ehHls: Boolean, posInicial: Long) {
    val b = MediaItem.Builder().setUri(endereco)
    if (ehHls) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    player.stop()
    player.setMediaItem(b.build())
    if (posInicial > 0) player.seekTo(posInicial)
    player.playWhenReady = true
    player.prepare()
  }

  /*
   * Conferencia antes de preparar o ExoPlayer: evita entregar HTML ou link
   * expirado ao HlsPlaylistParser (ParserException: Input does not start with
   * the #EXTM3U header) e renova a fonte automaticamente quando vencida.
   */
  LaunchedEffect(tentativa) {
    carregando = true
    falha = null
    refererRef.set(refererAtual)

    val ver = Fontes.verificar(urlAtual, refererAtual)
    if (ver is Fontes.ResultadoVerificacao.Valida) {
      urlAtual = ver.urlFinal
      prepararNoPlayer(ver.urlFinal, ver.ehHls, onde)
      return@LaunchedEffect
    }

    val motivo = (ver as Fontes.ResultadoVerificacao.Invalida).motivo
    if (!tentouRenovar && id > 0) {
      tentouRenovar = true
      Guardados.apagarFonte(ctx, tipo, id, temporada, episodio)
      val nova = try {
        Fontes.procurar(tipo, id, temporada, episodio, ignorarUrl = urlAtual)
      } catch (_: Exception) {
        null
      }
      if (nova != null) {
        Guardados.guardarFonte(ctx, nova)
        urlAtual = nova.url
        refererAtual = nova.referer
        refererRef.set(nova.referer)
        val verNova = Fontes.verificar(nova.url, nova.referer)
        if (verNova is Fontes.ResultadoVerificacao.Valida) {
          urlAtual = verNova.urlFinal
          prepararNoPlayer(verNova.urlFinal, verNova.ehHls, onde)
          return@LaunchedEffect
        }
      }
    }

    falha = "Nao deu para tocar este endereco ($motivo). " +
      "O servidor pode ter recusado o pedido ou o link expirou."
    carregando = false
    abertura = false
  }

  DisposableEffect(Unit) {
    val ouvinte = object : Player.Listener {
      override fun onIsPlayingChanged(estaTocando: Boolean) {
        tocando = estaTocando
        if (estaTocando) abertura = false
      }

      override fun onPlaybackStateChanged(estado: Int) {
        carregando = estado == Player.STATE_BUFFERING
        if (estado == Player.STATE_READY) abertura = false
      }

      override fun onVideoSizeChanged(tamanho: androidx.media3.common.VideoSize) {
        // A qualidade muda sozinha conforme a internet. Sem mostrar, a
        // pessoa veria a imagem piorar sem entender por que.
        qualidade = if (tamanho.height > 0) tamanho.height.toString() + "p" else ""
      }

      override fun onPlayerError(erro: PlaybackException) {
        if (player.currentPosition > 0) {
          onde = player.currentPosition
        }
        if (!tentouRenovar && id > 0) {
          tentouRenovar = true
          carregando = true
          falha = null
          escopo.launch {
            Guardados.apagarFonte(ctx, tipo, id, temporada, episodio)
            val nova = try {
              Fontes.procurar(tipo, id, temporada, episodio, ignorarUrl = urlAtual)
            } catch (_: Exception) {
              null
            }
            if (nova != null) {
              Guardados.guardarFonte(ctx, nova)
              urlAtual = nova.url
              refererAtual = nova.referer
              refererRef.set(nova.referer)
              tentativa++
            } else {
              falha = "Nao deu para tocar este endereco. " +
                "O servidor pode ter recusado o pedido ou o link expirou."
              carregando = false
              abertura = false
            }
          }
          return
        }
        falha = "Nao deu para tocar este endereco. " +
          "O servidor pode ter recusado o pedido ou o link expirou."
        carregando = false
        abertura = false
      }
    }
    player.addListener(ouvinte)
    onDispose {
      // Antes de fechar, guarda onde parou.
      val p = player.currentPosition
      val d = player.duration
      if (d > 0) {
        val marca =
          Marca(tipo, id, titulo, cartaz, temporada, episodio, p, d, System.currentTimeMillis())
        Guardados.marcar(ctx, marca)

        /*
         * E tambem para a conta, para o site e os outros aparelhos saberem.
         *
         * Vai solto de proposito: a tela esta fechando, e segurar o fecho
         * esperando a rede faria o player demorar a sumir. Se o pedido
         * falhar, o que ficou guardado aqui sobe na proxima vez.
         */
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
          try {
            Progresso.guardar(marca, terminou = marca.fracao >= 0.95f)
          } catch (_: Exception) {
          }
        }
      }
      player.removeListener(ouvinte)
      player.release()
    }
  }

  // O relogio do player: a posicao, a duracao e o quanto ja esta guardado.
  LaunchedEffect(Unit) {
    while (true) {
      onde = player.currentPosition
      quanto = if (player.duration > 0) player.duration else 0L
      guardado = player.bufferedPosition
      delay(500)
    }
  }

  // Fica quieto depois de uns segundos sem ninguem mexer.
  LaunchedEffect(ultimoToque, tocando) {
    quieto = false
    if (!tocando) return@LaunchedEffect
    delay(4000)
    quieto = true
  }

  // A abertura sai sozinha, mesmo que o filme demore.
  LaunchedEffect(Unit) {
    delay(2200)
    abertura = false
  }

  fun acordar() {
    ultimoToque = System.currentTimeMillis()
  }

  fun virarPlay() {
    acordar()
    if (player.isPlaying) player.pause() else player.play()
  }

  fun correr(ms: Long) {
    acordar()
    val alvo = (player.currentPosition + ms).coerceIn(0L, if (quanto > 0) quanto else Long.MAX_VALUE)
    player.seekTo(alvo)
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(Color.Black)
      .focusable()
      .onKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (evento.key) {
          Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
            virarPlay(); true
          }
          Key.DirectionLeft, Key.MediaRewind -> {
            correr(-10_000); true
          }
          Key.DirectionRight, Key.MediaFastForward -> {
            correr(10_000); true
          }
          Key.DirectionUp, Key.DirectionDown -> {
            acordar(); true
          }
          else -> false
        }
      }
      .clickable { if (quieto) acordar() else virarPlay() }
  ) {

    AndroidView(
      factory = { c ->
        SurfaceView(c).also { player.setVideoSurfaceView(it) }
      },
      modifier = Modifier.fillMaxSize()
    )

    /*
     * A abertura da marca, antes do filme.
     *
     * Fica por cima do palco e sai sozinha quando o filme comeca.
     */
    AnimatedVisibility(visible = abertura, label = "abertura") {
      Box(Modifier.fillMaxSize().background(Cor.bg), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("M", color = Cor.accent, fontSize = 64.sp, fontWeight = FontWeight.Black)
          Text("ovFlix", color = Cor.text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    // Enquanto o filme nao vem, a rodinha.
    if (carregando && !abertura && falha == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Rodinha(tamanho = 54) }
    }

    if (falha != null) {
      Box(Modifier.fillMaxSize().background(Color(0xEE0F172A)), contentAlignment = Alignment.Center) {
        Column(
          Modifier.padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("O filme nao abriu", color = Cor.text, fontSize = 18.sp,
            fontWeight = FontWeight.Bold)
          Vao(8)
          Text(falha!!, color = Cor.muted, fontSize = 13.sp)
          Vao(18)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.filmemeu.app.ui.Botao("Tentar novamente", jeito = com.filmemeu.app.ui.Jeito.PRIMARIO) {
              tentouRenovar = false
              falha = null
              carregando = true
              tentativa++
            }
            com.filmemeu.app.ui.Botao("Voltar", jeito = com.filmemeu.app.ui.Jeito.FANTASMA) {
              aoSair()
            }
          }
        }
      }
    }

    /* ----- a barra de cima ----- */
    AnimatedVisibility(
      visible = !quieto && !abertura,
      modifier = Modifier.align(Alignment.TopStart),
      label = "barra"
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .background(Color(0xCC0F172A))
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        com.filmemeu.app.ui.Botao("Voltar", jeito = com.filmemeu.app.ui.Jeito.FANTASMA,
          pequeno = true) { aoSair() }
        VaoH(10)
        Column(Modifier.weight(1f)) {
          Text(titulo, color = Cor.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            maxLines = 1)
          if (tipo.ehSerie) {
            Text("T" + temporada + " E" + episodio, color = Cor.muted, fontSize = 12.sp)
          }
        }
        /*
         * Pular para o proximo episodio.
         *
         * So em serie, e so aqui em cima, junto do nome - e o lugar onde a
         * pessoa ja olha para saber em que episodio esta.
         *
         * O proximo episodio e outro video: o endereco dele ainda nao
         * existe, e precisa ser procurado como o primeiro foi. Por isso a
         * tela abre de novo em vez de so trocar a fonte - assim o player
         * nasce ja com a posicao guardada daquele episodio, se houver, e o
         * Voltar leva de volta para a pagina do titulo, e nao para o
         * episodio anterior.
         */
        if (tipo.ehSerie) {
          com.filmemeu.app.ui.Botao(
            if (procurandoProximo) "Procurando…" else "Próximo ▸",
            pequeno = true
          ) {
            if (!procurandoProximo) {
              procurandoProximo = true
              escopo.launch {
                val proximo = episodio + 1
                val achada = try {
                  Fontes.procurar(tipo, id, temporada, proximo)
                } catch (e: Exception) {
                  null
                }
                procurandoProximo = false
                if (achada == null) {
                  android.widget.Toast.makeText(
                    ctx,
                    "Nao achei o episodio " + proximo + ".",
                    android.widget.Toast.LENGTH_SHORT
                  ).show()
                } else {
                  Guardados.guardarFonte(ctx, achada)
                  PlayerActivity.abrir(
                    ctx = ctx,
                    url = achada.url,
                    referer = achada.referer,
                    titulo = titulo,
                    cartaz = cartaz,
                    tipo = tipo,
                    id = id,
                    temporada = temporada,
                    episodio = proximo,
                    posicao = Guardados.posicaoDe(ctx, tipo, id, temporada, proximo)
                  )
                  aoSair()
                }
              }
            }
          }
          VaoH(8)
        }
      }
    }

    /* ----- os controles de baixo ----- */
    AnimatedVisibility(
      visible = !quieto && !abertura,
      modifier = Modifier.align(Alignment.BottomStart),
      label = "controles"
    ) {
      Column(
        Modifier
          .fillMaxWidth()
          .background(Color(0xCC0F172A))
          .padding(horizontal = 14.dp, vertical = 12.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          BotaoDoPlayer(if (tocando) "⏸" else "▶") { virarPlay() }
          VaoH(10)
          Text(
            tempo(onde) + " / " + tempo(quanto),
            color = Cor.text, fontSize = 13.sp
          )
          if (qualidade.isNotEmpty()) {
            VaoH(10)
            Text(
              qualidade,
              color = Cor.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
              modifier = Modifier
                .background(Cor.line, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }

          VaoH(12)
          Trilho(
            fracao = if (quanto > 0) onde.toFloat() / quanto else 0f,
            guardada = if (quanto > 0) guardado.toFloat() / quanto else 0f,
            modifier = Modifier.weight(1f)
          ) { f ->
            acordar()
            if (quanto > 0) player.seekTo((quanto * f).toLong())
          }

          VaoH(12)
          BotaoDoPlayer(if (mudo) "✕" else "♪") {
            acordar()
            mudo = !mudo
            player.volume = if (mudo) 0f else volume
          }
          VaoH(8)
          Trilho(
            fracao = if (mudo) 0f else volume,
            guardada = 0f,
            modifier = Modifier.width(90.dp)
          ) { f ->
            acordar()
            volume = f.coerceIn(0f, 1f)
            mudo = volume == 0f
            player.volume = volume
          }
        }
      }
    }

    /* ----- o nome do que esta tocando ----- */
    if (!quieto && !abertura && tipo.ehSerie) {
      Column(Modifier.align(Alignment.TopEnd).padding(top = 70.dp, end = 18.dp)) {
        Text(titulo, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
          "Temporada " + temporada + ", episodio " + episodio,
          color = Color(0xFFD7DEE8), fontSize = 13.sp
        )
      }
    }
  }
}

/** O botao redondo dos controles, igual ao `.np-bt` do site. */
@Composable
private fun BotaoDoPlayer(texto: String, aoTocar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  Box(
    Modifier
      .size(40.dp)
      .background(
        if (focado) Color(0x33FFFFFF) else Color(0x1AFFFFFF),
        RoundedCornerShape(50)
      )
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() },
    contentAlignment = Alignment.Center
  ) {
    Text(texto, color = Color.White, fontSize = 16.sp)
  }
}

/**
 * O trilho.
 *
 * Tres camadas, como no site: o que ja esta guardado adiante, o que ja foi
 * visto e a bolinha. A camada do guardado diz de relance se o filme tem
 * folego para seguir ou se esta andando no limite da conexao.
 */
@Composable
private fun Trilho(
  fracao: Float,
  guardada: Float,
  modifier: Modifier = Modifier,
  aoEscolher: (Float) -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()

  /*
   * O trilho tambem recebe o foco.
   *
   * Numa TV nao ha onde arrastar: quem anda no filme e a seta do controle,
   * e ela so chega aqui se este pedaco for focavel de verdade. O toque na
   * tela continua valendo para quem esta no celular.
   */
  Box(
    modifier
      .height(if (focado) 10.dp else 6.dp)
      .background(Color(0x33FFFFFF), RoundedCornerShape(50))
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoEscolher(fracao) }
  ) {
    Box(
      Modifier
        .fillMaxWidth(guardada.coerceIn(0f, 1f))
        .height(if (focado) 10.dp else 6.dp)
        .background(Color(0x55FFFFFF), RoundedCornerShape(50))
    )
    Box(
      Modifier
        .fillMaxWidth(fracao.coerceIn(0f, 1f))
        .height(if (focado) 10.dp else 6.dp)
        .background(Cor.accent, RoundedCornerShape(50))
    )
  }
}

/** 01:23:45, ou 23:45 quando nao passa de uma hora. */
private fun tempo(ms: Long): String {
  if (ms <= 0) return "00:00"
  val s = ms / 1000
  val h = s / 3600
  val m = (s % 3600) / 60
  val seg = s % 60
  return if (h > 0) {
    String.format("%02d:%02d:%02d", h, m, seg)
  } else {
    String.format("%02d:%02d", m, seg)
  }
}

/**
 * Envolve o AudioSink padrao para ressincronizar automaticamente quando um
 * stream HLS/MPEG-TS apresenta um salto de PTS de audio acima de 200ms entre
 * segmentos sem a tag `#EXT-X-DISCONTINUITY`.
 */
@OptIn(UnstableApi::class)
private class TolerantAudioSink(
  private val delegate: AudioSink
) : AudioSink by delegate {

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int
  ): Boolean {
    return try {
      delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    } catch (_: AudioSink.UnexpectedDiscontinuityException) {
      delegate.handleDiscontinuity()
      delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }
  }
}
