package com.filmemeu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.KeyboardOptions

/**
 * As cores do site, uma a uma.
 *
 * Sao as mesmas do :root do style.css. Mudando ali, muda aqui: e a unica
 * forma de o aplicativo e o site continuarem parecendo a mesma casa.
 */
object Cor {
  val bg = Color(0xFF0F172A)
  val bgSoft = Color(0xFF141F36)
  val panel = Color(0xFF18233C)
  val line = Color(0xFF1E293B)
  val lineSoft = Color(0xFF243150)
  val text = Color(0xFFF1F5F9)
  val muted = Color(0xFF94A3B8)
  val accent = Color(0xFF38BDF8)
  val accentStrong = Color(0xFF0EA5E9)
  val hls = Color(0xFFA78BFA)
  val mp4 = Color(0xFF34D399)
  val warn = Color(0xFFFBBF24)
  val danger = Color(0xFFF87171)

  /** A cor do texto dentro do botao primario, no site. */
  val sobreAccent = Color(0xFF04131F)
  val placeholder = Color(0xFF64748B)
}

/** --radius: 10px */
val raio = RoundedCornerShape(10.dp)
val raio8 = RoundedCornerShape(8.dp)
val raioPilula = RoundedCornerShape(999.dp)

val corpo = TextStyle(color = Cor.text, fontSize = 15.sp, lineHeight = 23.sp)

/**
 * Como o botao do site se parece.
 *
 * Tres jeitos, os mesmos tres do CSS: o comum, o primario e o fantasma. O
 * foco existe aqui de verdade, e nao so no hover, porque numa TV o controle
 * remoto nao tem onde passar o mouse: o anel de foco e a unica forma de
 * saber onde se esta.
 */
enum class Jeito { COMUM, PRIMARIO, FANTASMA, PERIGO }

@Composable
fun Botao(
  texto: String,
  modifier: Modifier = Modifier,
  jeito: Jeito = Jeito.COMUM,
  pequeno: Boolean = false,
  ligado: Boolean = true,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()

  val fundo = when (jeito) {
    Jeito.PRIMARIO -> if (focado) Cor.accent else Cor.accentStrong
    Jeito.FANTASMA -> Color.Transparent
    Jeito.PERIGO -> if (focado) Color(0xFF991B1B) else Color(0xFF7F1D1D)
    Jeito.COMUM -> if (focado) Cor.line else Cor.bgSoft
  }
  val letra = when (jeito) {
    Jeito.PRIMARIO -> Cor.sobreAccent
    Jeito.FANTASMA -> if (focado) Cor.text else Cor.muted
    Jeito.PERIGO -> Cor.text
    Jeito.COMUM -> Cor.text
  }
  val borda = when (jeito) {
    Jeito.PRIMARIO -> if (focado) Cor.accent else Cor.accentStrong
    Jeito.FANTASMA -> Color.Transparent
    Jeito.PERIGO -> Color(0xFF991B1B)
    Jeito.COMUM -> if (focado) Cor.accentStrong else Cor.lineSoft
  }

  Box(
    modifier = modifier
      .alpha(if (ligado) 1f else 0.55f)
      .border(1.dp, borda, raio8)
      .background(fundo, raio8)
      .focusable(enabled = ligado, interactionSource = fonte)
      .clickable(enabled = ligado, interactionSource = fonte, indication = null) { aoTocar() }
      .padding(
        horizontal = if (pequeno) 12.dp else 14.dp,
        vertical = if (pequeno) 8.dp else 11.dp
      ),
    contentAlignment = Alignment.Center
  ) {
    Text(
      texto,
      color = letra,
      fontSize = if (pequeno) 13.sp else 14.sp,
      fontWeight = FontWeight.SemiBold,
      maxLines = 1
    )
  }
}

/** O `link-button` do site: texto puro que se acende ao receber o foco. */
@Composable
fun BotaoTexto(
  texto: String,
  modifier: Modifier = Modifier,
  tamanho: Int = 13,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  Text(
    texto,
    color = if (focado) Cor.text else Cor.muted,
    fontSize = tamanho.sp,
    modifier = modifier
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(vertical = 6.dp, horizontal = 4.dp)
  )
}

/**
 * O campo de texto do site.
 *
 * O contorno acende no foco, igual ao `:focus` do CSS: numa TV e por ele
 * que a pessoa sabe que o que digitar vai cair ali.
 */
@Composable
fun Campo(
  rotulo: String?,
  valor: String,
  aoMudar: (String) -> Unit,
  modifier: Modifier = Modifier,
  dica: String = "",
  senha: Boolean = false,
  email: Boolean = false,
  numero: Boolean = false,
  acaoDoTeclado: ImeAction = ImeAction.Next
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()

  androidx.compose.foundation.layout.Column(modifier) {
    if (rotulo != null) {
      Text(
        rotulo,
        color = Cor.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
      )
    }
    Box(
      Modifier
        .fillMaxWidth()
        .border(if (focado) 2.dp else 1.dp, if (focado) Cor.accentStrong else Cor.lineSoft, raio8)
        .background(Cor.bg, raio8)
        .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
      if (valor.isEmpty() && dica.isNotEmpty()) {
        Text(dica, color = Cor.placeholder, fontSize = 14.sp)
      }
      BasicTextField(
        value = valor,
        onValueChange = aoMudar,
        singleLine = true,
        interactionSource = fonte,
        textStyle = LocalTextStyle.current.copy(color = Cor.text, fontSize = 14.sp),
        cursorBrush = SolidColor(Cor.accent),
        visualTransformation =
          if (senha) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
          keyboardType = when {
            senha -> KeyboardType.Password
            email -> KeyboardType.Email
            numero -> KeyboardType.Number
            else -> KeyboardType.Text
          },
          imeAction = acaoDoTeclado
        ),
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

/** O selo do cartaz: FILME, SERIE, ANIME. */
@Composable
fun Selo(texto: String, cor: Color = Cor.accent, modifier: Modifier = Modifier) {
  Text(
    texto,
    color = cor,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    modifier = modifier
      .background(Color(0xCC0F172A), raioPilula)
      .padding(horizontal = 7.dp, vertical = 3.dp)
  )
}

/**
 * A ficha de categoria, com o estado ligado do site.
 *
 * Ligada, ela fica com a cor de destaque e a borda acesa; e o `is-on` do
 * `.genre-chip`.
 */
@Composable
fun Ficha(
  texto: String,
  ligada: Boolean,
  modifier: Modifier = Modifier,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focada by fonte.collectIsFocusedAsState()
  Box(
    modifier
      .border(1.dp, if (ligada || focada) Cor.accentStrong else Cor.lineSoft, raioPilula)
      .background(if (ligada) Cor.accentStrong else Cor.bgSoft, raioPilula)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(horizontal = 14.dp, vertical = 7.dp)
  ) {
    Text(
      texto,
      color = if (ligada) Cor.sobreAccent else if (focada) Cor.text else Cor.muted,
      fontSize = 13.sp,
      fontWeight = if (ligada) FontWeight.SemiBold else FontWeight.Normal,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/**
 * A troca de abas do catalogo: Inicio, Filmes, Series, Animacoes, Minha.
 *
 * E o `.kind-switch` do site, com a aba escolhida sublinhada pela cor de
 * destaque.
 */
@Composable
fun Abas(
  nomes: List<String>,
  escolhida: Int,
  modifier: Modifier = Modifier,
  aoEscolher: (Int) -> Unit
) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    nomes.forEachIndexed { i, nome ->
      val fonte = remember(i) { MutableInteractionSource() }
      val focada by fonte.collectIsFocusedAsState()
      val ligada = i == escolhida
      Box(
        Modifier
          .background(if (ligada) Cor.line else Color.Transparent, raio8)
          .border(1.dp, if (focada) Cor.accentStrong else Color.Transparent, raio8)
          .focusable(interactionSource = fonte)
          .clickable(interactionSource = fonte, indication = null) { aoEscolher(i) }
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Text(
          nome,
          color = if (ligada) Cor.accent else if (focada) Cor.text else Cor.muted,
          fontSize = 14.sp,
          fontWeight = if (ligada) FontWeight.Bold else FontWeight.SemiBold,
          maxLines = 1
        )
      }
    }
  }
}

/** A rodinha do site, enquanto o filme nao vem. */
@Composable
fun Rodinha(modifier: Modifier = Modifier, tamanho: Int = 34) {
  androidx.compose.material3.CircularProgressIndicator(
    modifier = modifier.size(tamanho.dp),
    color = Cor.accent,
    strokeWidth = 3.dp
  )
}
