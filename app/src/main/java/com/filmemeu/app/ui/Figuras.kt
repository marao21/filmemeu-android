package com.filmemeu.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/*
 * As figuras de perfil - as mesmas do site, uma por uma.
 *
 * O site oferece setenta e oito: dezessete rostos que desenhamos em SVG e
 * sessenta e uma caricaturas. Ate aqui o aplicativo tinha seis rostos
 * proprios, parecidos mas nao iguais - e um perfil feito na TV aparecia no
 * telefone com outra cara, porque o nome guardado no banco nao existia
 * deste lado.
 *
 * Por isso este arquivo copia o do site ao pe da letra: os mesmos nomes, os
 * mesmos desenhos, as mesmas cores e ate a mesma conta que decide para onde
 * vai um nome desconhecido. Quem escolher uma cara na web ve aquela cara
 * aqui.
 */

/* ----- o traco de cada desenho ----- */

/*
 * Um pedaco de desenho, nas medidas do SVG do site (quadro de 24 por 24).
 *
 * No site o grupo inteiro leva `fill` e `stroke` da mesma tinta: quem nao
 * diz `fill="none"` sai preenchido E contornado com a grossura 1, que e o
 * padrao do SVG. E essa borda de um ponto que engorda os olhos; sem ela os
 * rostos sairiam magros demais. Daqui o `contorno` do traco cheio.
 */
private sealed interface Traco {
  data class Circulo(
    val cx: Float, val cy: Float, val r: Float,
    val grossura: Float = 0f,
    val branco: Boolean = false
  ) : Traco

  data class Retangulo(
    val x: Float, val y: Float, val larg: Float, val alt: Float, val raio: Float
  ) : Traco

  data class Elipse(
    val cx: Float, val cy: Float, val rx: Float, val ry: Float, val grossura: Float
  ) : Traco

  data class Caminho(
    val d: String, val grossura: Float = 0f
  ) : Traco
}

/** Grossura 0 quer dizer cheio: preenche e contorna com um ponto, como no SVG. */
private val CHEIO = 0f

private fun circulo(cx: Float, cy: Float, r: Float) = Traco.Circulo(cx, cy, r)
private fun aro(cx: Float, cy: Float, r: Float, g: Float) = Traco.Circulo(cx, cy, r, g)
private fun risco(d: String, g: Float) = Traco.Caminho(d, g)
private fun massa(d: String) = Traco.Caminho(d, CHEIO)

/*
 * Os dezessete rostos, copiados do site sem mudar uma virgula.
 *
 * Os numeros sao os do SVG de la; aqui eles so sao multiplicados pelo lado
 * do quadrado na hora de desenhar, entao a cara cresce sem perder a forma.
 */
private val FIGURAS_DESENHADAS: Map<String, List<Traco>> = mapOf(
  "sorriso" to listOf(
    circulo(8.8f, 9.6f, 1.7f), circulo(15.2f, 9.6f, 1.7f),
    risco("M8.2 14.4c1.1 1.3 2.3 1.9 3.8 1.9s2.7-.6 3.8-1.9", 1.8f)
  ),
  "piscada" to listOf(
    circulo(8.8f, 9.6f, 1.7f),
    risco("M13.6 9.6c.5-.7 1.7-.7 2.2 0", 1.8f),
    risco("M8.2 14.4c1.1 1.3 2.3 1.9 3.8 1.9s2.7-.6 3.8-1.9", 1.8f)
  ),
  "alegre" to listOf(
    risco("M7.4 10.2c.5-1.1 1.9-1.1 2.4 0M14.2 10.2c.5-1.1 1.9-1.1 2.4 0", 1.8f),
    massa("M8 13.8h8c0 2.2-1.8 3.4-4 3.4s-4-1.2-4-3.4z")
  ),
  "surpresa" to listOf(
    circulo(8.8f, 9.4f, 1.7f), circulo(15.2f, 9.4f, 1.7f),
    Traco.Elipse(12f, 15.2f, 2f, 2.4f, 1.7f)
  ),
  "oculos" to listOf(
    aro(8.6f, 10f, 2.6f, 1.6f), aro(15.4f, 10f, 2.6f, 1.6f),
    risco("M11.2 10h1.6", 1.6f),
    risco("M9 15.4h6", 1.8f)
  ),
  "sono" to listOf(
    risco("M7.2 10.4c.8.9 2.2.9 3 0M13.8 10.4c.8.9 2.2.9 3 0", 1.8f),
    risco("M10.4 15.4h3.2", 1.8f)
  ),
  "serio" to listOf(
    circulo(8.8f, 9.6f, 1.7f), circulo(15.2f, 9.6f, 1.7f),
    risco("M9 15h6", 1.8f)
  ),
  "robo" to listOf(
    Traco.Retangulo(6.8f, 8f, 3.4f, 3.4f, 0.7f),
    Traco.Retangulo(13.8f, 8f, 3.4f, 3.4f, 0.7f),
    risco("M8 14.6h8M10.4 14.6v2.4M13.6 14.6v2.4", 1.6f)
  ),
  "gato" to listOf(
    risco("M6.2 7.4l1.2 3.2M17.8 7.4l-1.2 3.2", 1.8f),
    circulo(9f, 11.4f, 1.5f), circulo(15f, 11.4f, 1.5f),
    massa("M12 14.2l-1.2 1h2.4z"),
    risco("M12 15.2v1.2M12 16.4c-.8.8-2 .8-2.8 0M12 16.4c.8.8 2 .8 2.8 0", 1.4f)
  ),
  "urso" to listOf(
    aro(6.8f, 7.6f, 2.1f, 1.6f), aro(17.2f, 7.6f, 2.1f, 1.6f),
    circulo(9.6f, 11.2f, 1.4f), circulo(14.4f, 11.2f, 1.4f),
    Traco.Elipse(12f, 15.4f, 3.2f, 2.4f, 1.5f),
    circulo(12f, 14.4f, 0.9f)
  ),
  "coruja" to listOf(
    aro(8.8f, 10.4f, 3f, 1.5f), aro(15.2f, 10.4f, 3f, 1.5f),
    circulo(8.8f, 10.4f, 1.2f), circulo(15.2f, 10.4f, 1.2f),
    massa("M12 13.6l-1 1.6h2z")
  ),
  "oculosEscuros" to listOf(
    massa("M5.6 9.2h12.8v1.2a3 3 0 0 1-3 3h-1.2a2 2 0 0 1-2-2 2 2 0 0 1-2 2H8.6a3 3 0 0 1-3-3z"),
    risco("M9 16.6c1.6 1.2 4.4 1.2 6 0", 1.7f)
  ),
  "bigode" to listOf(
    circulo(8.8f, 8.8f, 1.5f), circulo(15.2f, 8.8f, 1.5f),
    massa(
      "M12 13.2c-1.4-1.6-4.2-1.4-4.6.4-.3 1.4 1.6 2.2 2.8 1.2" +
        "M12 13.2c1.4-1.6 4.2-1.4 4.6.4.3 1.4-1.6 2.2-2.8 1.2"
    ),
    risco("M10.6 17h2.8", 1.5f)
  ),
  "chapeu" to listOf(
    risco("M4.8 8.4h14.4", 1.7f),
    massa("M8 8.4V6.2a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2.2z"),
    circulo(9.4f, 12.6f, 1.4f), circulo(14.6f, 12.6f, 1.4f),
    risco("M9.4 16.4c1.4 1.2 3.8 1.2 5.2 0", 1.6f)
  ),
  "fones" to listOf(
    risco("M6 12V10a6 6 0 0 1 12 0v2", 1.7f),
    Traco.Retangulo(4.4f, 11.4f, 2.8f, 4.4f, 1.2f),
    Traco.Retangulo(16.8f, 11.4f, 2.8f, 4.4f, 1.2f),
    circulo(10f, 13f, 1.2f), circulo(14f, 13f, 1.2f),
    risco("M10.4 16.6h3.2", 1.5f)
  ),
  "estrela" to listOf(
    massa("M8.8 7.6l.7 1.5 1.6.2-1.2 1.1.3 1.6-1.4-.8-1.4.8.3-1.6-1.2-1.1 1.6-.2z"),
    massa("M15.2 7.6l.7 1.5 1.6.2-1.2 1.1.3 1.6-1.4-.8-1.4.8.3-1.6-1.2-1.1 1.6-.2z"),
    risco("M8.4 14.8c1.6 1.8 5.6 1.8 7.2 0", 1.8f)
  ),
  "fantasma" to listOf(
    massa("M6.6 17.4V10a5.4 5.4 0 0 1 10.8 0v7.4l-1.8-1.4-1.8 1.4-1.8-1.4-1.8 1.4-1.8-1.4z"),
    Traco.Circulo(9.8f, 10.2f, 1.1f, branco = true),
    Traco.Circulo(14.2f, 10.2f, 1.1f, branco = true)
  )
)

/** Os nomes dos rostos desenhados, na ordem em que o site os mostra. */
val FIGURAS_SVG: List<String> = listOf(
  "sorriso", "piscada", "alegre", "surpresa", "oculos", "sono",
  "serio", "robo", "gato", "urso", "coruja", "oculosEscuros",
  "bigode", "chapeu", "fones", "estrela", "fantasma"
)

/*
 * As caricaturas: sessenta e uma, as mesmas do site e na mesma ordem.
 *
 * As imagens vao dentro do aplicativo, em assets/perfis. Sao 424 KB no
 * total - menos do que um cartaz - e assim a tela de perfis se desenha
 * inteira sem pedir nada a rede, que e o que faz diferenca na TV Box.
 */
val CARICATURAS: List<String> = listOf(
  "r01", "r04", "r06", "r07", "r08", "r09", "r10", "r11", "r12", "r13",
  "r14", "r17", "r18", "r19", "r20", "r21", "r22", "r23", "r24", "r25",
  "r26", "g201", "g202", "g203", "g204", "g205", "g206", "g207", "g208", "g209",
  "g210", "g211", "g212", "g213", "g214", "g215", "g216", "g217", "g218", "g219",
  "g220", "g301", "g302", "g303", "g304", "g305", "g306", "g307", "g308", "g309",
  "g310", "g311", "g312", "g313", "g314", "g315", "g316", "g317", "g318", "g319",
  "g320"
)

/** Todas, na ordem do editor do site: primeiro os rostos, depois as caricaturas. */
val FIGURAS: List<String> = FIGURAS_SVG + CARICATURAS

/** As sete cores que o site oferece - nem uma a mais. */
val CORES: List<String> = listOf(
  "#38bdf8", "#f59e0b", "#34d399", "#f472b6", "#a78bfa", "#fb7185", "#facc15"
)

fun ehCaricatura(id: String): Boolean = CARICATURAS.contains(id)

/*
 * O perfil guarda o NOME da figura, e o conjunto ja mudou uma vez.
 *
 * Um nome que nenhum dos dois lados reconhece precisa virar a MESMA cara
 * aqui e no site, senao o mesmo perfil aparece diferente em cada aparelho.
 * Por isso a conta abaixo e, numero por numero, a que o site faz.
 */
fun figuraDeVerdade(id: String?): String {
  val texto = id ?: ""
  if (ehCaricatura(texto) || FIGURAS_DESENHADAS.containsKey(texto)) return texto
  if (CARICATURAS.isEmpty()) return "sorriso"
  var soma = 0L
  for (c in texto) soma = (soma * 31 + c.code) % 100000
  return CARICATURAS[(soma % CARICATURAS.size).toInt()]
}

fun corDeTexto(hex: String): Color = try {
  Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
  Cor.accent
}

/* ----- desenhar ----- */

private fun DrawScope.desenhar(tracos: List<Traco>, tinta: Color, lado: Float) {
  val k = lado / 24f
  tracos.forEach { t ->
    when (t) {
      is Traco.Circulo -> {
        val cor = if (t.branco) Color.White else tinta
        val centro = Offset(t.cx * k, t.cy * k)
        if (t.grossura > 0f) {
          drawCircle(cor, t.r * k, centro, style = Stroke(t.grossura * k))
        } else {
          drawCircle(cor, t.r * k, centro)
          // A borda de um ponto que o SVG poe de graca.
          if (!t.branco) drawCircle(cor, t.r * k, centro, style = Stroke(k))
        }
      }

      is Traco.Retangulo -> drawRoundRect(
        tinta,
        Offset(t.x * k, t.y * k),
        Size(t.larg * k, t.alt * k),
        androidx.compose.ui.geometry.CornerRadius(t.raio * k)
      )

      is Traco.Elipse -> drawOval(
        tinta,
        Offset((t.cx - t.rx) * k, (t.cy - t.ry) * k),
        Size(t.rx * 2 * k, t.ry * 2 * k),
        style = Stroke(t.grossura * k)
      )

      is Traco.Caminho -> {
        val caminho = PathParser().parsePathString(t.d).toPath()
        val matriz = android.graphics.Matrix().apply { setScale(k, k) }
        caminho.asAndroidPath().transform(matriz)
        if (t.grossura > 0f) {
          drawPath(caminho, tinta, style = Stroke(t.grossura * k, cap = StrokeCap.Round))
        } else {
          drawPath(caminho, tinta)
          drawPath(caminho, tinta, style = Stroke(k))
        }
      }
    }
  }
}

/**
 * A carinha do perfil, do tamanho pedido.
 *
 * Uma caricatura e imagem e preenche o quadrado inteiro; um rosto desenhado
 * vem sobre o quadrado da cor do perfil, como no site.
 */
@Composable
fun Cara(figura: String, cor: String, tamanho: Int, modifier: Modifier = Modifier) {
  val id = figuraDeVerdade(figura)
  // rx=5 num quadro de 24: o mesmo canto arredondado do site.
  val canto = RoundedCornerShape((tamanho * 5f / 24f).dp)

  if (ehCaricatura(id)) {
    AsyncImage(
      model = "file:///android_asset/perfis/$id.webp",
      contentDescription = null,
      modifier = modifier.size(tamanho.dp).clip(canto),
      contentScale = ContentScale.Crop
    )
    return
  }

  val tracos = FIGURAS_DESENHADAS[id] ?: FIGURAS_DESENHADAS.getValue("sorriso")
  // A tinta do site: escura, a mesma em toda cor de fundo.
  val tinta = Color(0xFF0B1220)

  Box(
    modifier
      .size(tamanho.dp)
      .background(corDeTexto(cor), canto)
      .drawBehind { desenhar(tracos, tinta, size.width) }
  )
}
