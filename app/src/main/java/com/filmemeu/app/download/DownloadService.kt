package com.filmemeu.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.filmemeu.app.R
import com.filmemeu.app.media.Medida
import com.filmemeu.app.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * O download, feito pelo proprio aparelho.
 *
 * No site, baixar HLS era montar o arquivo no navegador, pedaco por pedaco,
 * e o navegador segurava tudo na memoria. Aqui o trabalho acontece num
 * servico do Android: escreve direto no arquivo, continua de onde parou e
 * sobrevive a tela apagar.
 *
 * Em MP4, um pedido com Range, que retoma. Em HLS, os segmentos da playlist
 * um apos o outro, grudados num arquivo .ts — que e o que um .m3u8 e, por
 * dentro.
 */
class DownloadService : Service() {

  companion object {
    private const val CANAL = "downloads"
    private const val AVISO = 4321

    private const val URL = "url"
    private const val REFERER = "referer"
    private const val NOME = "nome"

    /** Manda baixar. O servico se levanta sozinho. */
    fun baixar(ctx: Context, url: String, referer: String?, nome: String) {
      val i = Intent(ctx, DownloadService::class.java)
      i.putExtra(URL, url)
      i.putExtra(REFERER, referer)
      i.putExtra(NOME, nome)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ctx.startForegroundService(i)
      } else {
        ctx.startService(i)
      }
    }
  }

  private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var avisos: NotificationManager

  override fun onCreate() {
    super.onCreate()
    avisos = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      avisos.createNotificationChannel(
        NotificationChannel(CANAL, "Downloads", NotificationManager.IMPORTANCE_LOW)
      )
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, bandeiras: Int, id: Int): Int {
    val url = intent?.getStringExtra(URL)
    val referer = intent?.getStringExtra(REFERER)
    val nome = intent?.getStringExtra(NOME) ?: "video"

    if (url == null) {
      stopSelf()
      return START_NOT_STICKY
    }

    startForeground(AVISO, aviso("Baixando " + nome, "Comecando...", 0))

    escopo.launch {
      try {
        val arquivo = if (Medida.pareceHls(url)) {
          baixarHls(url, referer, nome)
        } else {
          baixarDireto(url, referer, nome)
        }
        pronto("Baixado: " + arquivo.name)
      } catch (e: Exception) {
        pronto("O download falhou: " + (e.message ?: "sem detalhe"))
      }
      stopSelf()
    }

    return START_NOT_STICKY
  }

  /** A pasta onde o filme fica, visivel para o gerenciador de arquivos. */
  private fun pasta(): File {
    val raiz = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
      ?: File(filesDir, "filmes")
    if (!raiz.exists()) raiz.mkdirs()
    return raiz
  }

  private fun limpar(nome: String): String =
    nome.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifEmpty { "video" }

  /**
   * MP4 e qualquer arquivo direto.
   *
   * Existindo pedaco de um download anterior, continua dele com Range em
   * vez de comecar de novo: numa rede que cai, isso e a diferenca entre
   * terminar e nunca terminar.
   */
  private suspend fun baixarDireto(url: String, referer: String?, nome: String): File {
    val destino = File(pasta(), limpar(nome) + ".mp4")
    val jaTem = if (destino.exists()) destino.length() else 0L

    val b = Request.Builder().url(url).header("user-agent", Http.UA)
    if (!referer.isNullOrEmpty()) b.header("referer", referer)
    if (jaTem > 0) b.header("range", "bytes=" + jaTem + "-")

    Http.cliente.newCall(b.build()).execute().use { r ->
      if (!r.isSuccessful && r.code != 206) throw Exception("o servidor respondeu " + r.code)
      val corpo = r.body ?: throw Exception("resposta sem conteudo")

      // 206 quer dizer que ele aceitou continuar de onde paramos.
      val continuando = r.code == 206 && jaTem > 0
      val total = corpo.contentLength() + (if (continuando) jaTem else 0L)

      FileOutputStream(destino, continuando).use { saida ->
        corpo.byteStream().use { entrada ->
          val balde = ByteArray(128 * 1024)
          var feito = if (continuando) jaTem else 0L
          var ultimoAviso = 0L

          while (true) {
            val quantos = entrada.read(balde)
            if (quantos <= 0) break
            saida.write(balde, 0, quantos)
            feito += quantos

            val agora = System.currentTimeMillis()
            if (agora - ultimoAviso > 1000) {
              ultimoAviso = agora
              val porcento = if (total > 0) ((feito * 100) / total).toInt() else 0
              avisos.notify(
                AVISO,
                aviso("Baixando " + nome, porcento.toString() + "%", porcento)
              )
            }
          }
        }
      }
    }
    return destino
  }

  /**
   * HLS: os segmentos, um apos o outro.
   *
   * Sendo uma master playlist, ela nao tem segmento nenhum: tem outras
   * playlists dentro. Nesse caso seguimos a primeira variante, igual ao
   * que a medida de duracao faz.
   */
  private suspend fun baixarHls(url: String, referer: String?, nome: String): File {
    val cabecalhos = if (referer.isNullOrEmpty()) emptyMap() else mapOf("Referer" to referer)
    var endereco = url
    var texto = Http.texto(endereco, cabecalhos)

    var pedacos = segmentosDe(texto, endereco)
    if (pedacos.isEmpty()) {
      val variante = texto.lines().map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: throw Exception("a playlist nao tem segmento nenhum")
      endereco = Medida.resolver(endereco, variante)
      texto = Http.texto(endereco, cabecalhos)
      pedacos = segmentosDe(texto, endereco)
    }
    if (pedacos.isEmpty()) throw Exception("a playlist nao tem segmento nenhum")

    val destino = File(pasta(), limpar(nome) + ".ts")
    FileOutputStream(destino, false).use { saida ->
      pedacos.forEachIndexed { i, pedaco ->
        val b = Request.Builder().url(pedaco).header("user-agent", Http.UA)
        if (!referer.isNullOrEmpty()) b.header("referer", referer)

        Http.cliente.newCall(b.build()).execute().use { r ->
          if (!r.isSuccessful) throw Exception("o segmento " + (i + 1) + " respondeu " + r.code)
          r.body?.byteStream()?.use { it.copyTo(saida, 128 * 1024) }
        }

        val porcento = ((i + 1) * 100) / pedacos.size
        avisos.notify(
          AVISO,
          aviso(
            "Baixando " + nome,
            "Parte " + (i + 1) + " de " + pedacos.size,
            porcento
          )
        )
      }
    }
    return destino
  }

  private fun segmentosDe(playlist: String, base: String): List<String> =
    playlist.lines().map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") }
      .filter { !it.endsWith(".m3u8") }
      .map { Medida.resolver(base, it) }

  private fun aviso(titulo: String, texto: String, porcento: Int): Notification =
    NotificationCompat.Builder(this, CANAL)
      .setContentTitle(titulo)
      .setContentText(texto)
      .setSmallIcon(R.drawable.ic_launcher)
      .setOngoing(true)
      .setProgress(100, porcento, porcento == 0)
      .build()

  private fun pronto(texto: String) {
    avisos.notify(
      AVISO + 1,
      NotificationCompat.Builder(this, CANAL)
        .setContentTitle("MovFlix")
        .setContentText(texto)
        .setSmallIcon(R.drawable.ic_launcher)
        .setAutoCancel(true)
        .build()
    )
  }
}
