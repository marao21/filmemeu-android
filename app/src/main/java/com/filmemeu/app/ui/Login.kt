package com.filmemeu.app.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmemeu.app.data.Conta
import kotlinx.coroutines.launch

/**
 * A porta de entrada, responsiva para celular na horizontal, tablet e TV.
 *
 * Em telas largas (paisagem / tablet / TV), divide a marca a esquerda e o
 * formulario a direita para caber inteiro sem cortar os botoes de baixo.
 * Em telas estreitas, empilha numa coluna com rolagem.
 */
@Composable
fun TelaLogin(aoEntrar: () -> Unit) {
  val ctx = LocalContext.current
  val escopo = rememberCoroutineScope()

  var criando by remember { mutableStateOf(false) }
  var esqueci by remember { mutableStateOf(false) }

  var nome by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var senha by remember { mutableStateOf("") }
  var recado by remember { mutableStateOf<String?>(null) }
  var erro by remember { mutableStateOf(false) }
  var trabalhando by remember { mutableStateOf(false) }

  fun enviar() {
    if (trabalhando) return
    recado = null
    erro = false

    if (esqueci) {
      if (email.isBlank()) {
        recado = "Escreva o e-mail da conta."
        erro = true
        return
      }
      trabalhando = true
      escopo.launch {
        recado = Conta.recuperarSenha(email)
        trabalhando = false
      }
      return
    }

    if (email.isBlank() || senha.length < 6) {
      recado = "Informe o e-mail e uma senha de pelo menos 6 caracteres."
      erro = true
      return
    }
    if (criando && nome.isBlank()) {
      recado = "Diga como quer ser chamado."
      erro = true
      return
    }

    trabalhando = true
    escopo.launch {
      val falha = if (criando) {
        Conta.criar(ctx, nome, email, senha)
      } else {
        Conta.entrar(ctx, email, senha)
      }
      trabalhando = false
      if (falha == null) {
        aoEntrar()
      } else {
        recado = falha
        erro = !falha.startsWith("Conta criada")
      }
    }
  }

  val subtitulo = if (esqueci) {
    "Escreva o e-mail da conta e enviamos um link para criar outra senha."
  } else {
    "Entre com a sua conta para continuar."
  }

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(Cor.bg)
      .imePadding()
  ) {
    val telaLarga = maxWidth >= 560.dp
    val alturaCompacta = maxHeight < 460.dp
    val larguraMax = if (maxWidth >= 900.dp) 800.dp else 720.dp
    val paddingExterno = if (alturaCompacta) 12.dp else 24.dp
    val paddingPainel = if (alturaCompacta) 18.dp else 26.dp
    val espacoCampo = if (alturaCompacta) 8.dp else 12.dp

    @Composable
    fun Formulario(modifier: Modifier = Modifier) {
      Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (!esqueci) {
          Row(
            Modifier
              .fillMaxWidth()
              .background(Cor.bg, raio8)
              .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            AbaDaConta("Entrar", !criando, Modifier.weight(1f), compacto = alturaCompacta) {
              criando = false; recado = null
            }
            AbaDaConta("Criar conta", criando, Modifier.weight(1f), compacto = alturaCompacta) {
              criando = true; recado = null
            }
          }
          Spacer(Modifier.height(if (alturaCompacta) 10.dp else 16.dp))
        }

        if (criando && !esqueci) {
          Campo(
            "Nome", nome, { nome = it },
            Modifier.fillMaxWidth(),
            dica = "Como quer ser chamado",
            compacto = alturaCompacta
          )
          Spacer(Modifier.height(espacoCampo))
        }

        Campo(
          "E-mail", email, { email = it },
          Modifier.fillMaxWidth(),
          dica = "voce@exemplo.com",
          email = true,
          compacto = alturaCompacta,
          acaoDoTeclado = if (esqueci) ImeAction.Done else ImeAction.Next,
          aoEnviar = if (esqueci) ({ enviar() }) else null
        )

        if (!esqueci) {
          Spacer(Modifier.height(espacoCampo))
          Campo(
            "Senha", senha, { senha = it },
            Modifier.fillMaxWidth(),
            dica = "Pelo menos 6 caracteres",
            senha = true,
            compacto = alturaCompacta,
            acaoDoTeclado = ImeAction.Done,
            aoEnviar = { enviar() }
          )
        }

        if (recado != null) {
          Spacer(Modifier.height(if (alturaCompacta) 8.dp else 12.dp))
          Text(
            recado!!,
            color = if (erro) Cor.danger else Cor.muted,
            fontSize = if (alturaCompacta) 12.sp else 13.sp,
            textAlign = TextAlign.Center
          )
        }

        Spacer(Modifier.height(if (alturaCompacta) 12.dp else 18.dp))
        Botao(
          when {
            trabalhando -> "Um instante..."
            esqueci -> "Enviar o link"
            criando -> "Criar conta"
            else -> "Entrar"
          },
          Modifier.fillMaxWidth(),
          Jeito.PRIMARIO,
          pequeno = alturaCompacta,
          ligado = !trabalhando
        ) { enviar() }

        Spacer(Modifier.height(if (alturaCompacta) 6.dp else 12.dp))
        if (esqueci) {
          BotaoTexto("Voltar para entrar", tamanho = if (alturaCompacta) 12 else 13) {
            esqueci = false; recado = null
          }
        } else {
          BotaoTexto("Esqueci minha senha", tamanho = if (alturaCompacta) 12 else 13) {
            esqueci = true; recado = null
          }
        }
      }
    }

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(paddingExterno),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (telaLarga) {
        Row(
          Modifier
            .widthIn(max = larguraMax)
            .fillMaxWidth()
            .border(1.dp, Cor.lineSoft, raio)
            .background(Cor.panel, raio)
            .padding(paddingPainel),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(
            Modifier
              .weight(0.40f)
              .padding(end = if (alturaCompacta) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            MarcaMovFlix(tamanho = if (alturaCompacta) 48 else 60)
            Spacer(Modifier.height(if (alturaCompacta) 10.dp else 14.dp))
            Text(
              "MovFlix",
              color = Cor.text,
              fontSize = if (alturaCompacta) 22.sp else 28.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
              subtitulo,
              color = Cor.muted,
              fontSize = if (alturaCompacta) 12.5.sp else 14.sp,
              textAlign = TextAlign.Center
            )
          }

          Box(
            Modifier
              .width(1.dp)
              .height(if (alturaCompacta) 160.dp else 210.dp)
              .background(Cor.lineSoft)
          )

          Formulario(
            Modifier
              .weight(0.60f)
              .padding(start = if (alturaCompacta) 16.dp else 24.dp)
          )
        }
      } else {
        Column(
          Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .border(1.dp, Cor.lineSoft, raio)
            .background(Cor.panel, raio)
            .padding(paddingPainel),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          MarcaMovFlix(tamanho = if (alturaCompacta) 44 else 56)
          Spacer(Modifier.height(if (alturaCompacta) 8.dp else 14.dp))
          Text(
            "MovFlix",
            color = Cor.text,
            fontSize = if (alturaCompacta) 22.sp else 26.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(Modifier.height(4.dp))
          Text(
            subtitulo,
            color = Cor.muted,
            fontSize = if (alturaCompacta) 12.5.sp else 14.sp,
            textAlign = TextAlign.Center
          )
          Spacer(Modifier.height(if (alturaCompacta) 12.dp else 18.dp))
          Formulario(Modifier.fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun AbaDaConta(
  texto: String,
  ligada: Boolean,
  modifier: Modifier = Modifier,
  compacto: Boolean = false,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focada by fonte.collectIsFocusedAsState()
  Box(
    modifier
      .background(if (ligada) Cor.panel else Color.Transparent, raio8)
      .border(1.dp, if (focada) Cor.accentStrong else Color.Transparent, raio8)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(vertical = if (compacto) 7.dp else 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      texto,
      color = if (ligada) Cor.accent else Cor.muted,
      fontSize = if (compacto) 13.sp else 14.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

/**
 * A marca da casa: o M em destaque, como no site.
 *
 * Desenho nosso, feito de formas simples para ler bem pequeno na barra e de
 * longe na TV.
 */
@Composable
fun MarcaMovFlix(tamanho: Int = 40, modifier: Modifier = Modifier) {
  Box(
    modifier
      .size(tamanho.dp)
      .background(Cor.accent, RoundedCornerShape((tamanho / 4).dp)),
    contentAlignment = Alignment.Center
  ) {
    Text(
      "M",
      color = Cor.bg,
      fontSize = (tamanho * 0.58f).sp,
      fontWeight = FontWeight.Black
    )
  }
}

/** O nome escrito como no site: o M aceso, o resto claro. */
@Composable
fun NomeMovFlix(tamanho: Int = 20, modifier: Modifier = Modifier) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Text("M", color = Cor.accent, fontSize = tamanho.sp, fontWeight = FontWeight.Black)
    Text("ovFlix", color = Cor.text, fontSize = tamanho.sp, fontWeight = FontWeight.Bold)
  }
}

/** Um espaco em branco com a altura pedida, para o ritmo do site. */
@Composable
fun Vao(altura: Int) {
  Spacer(Modifier.height(altura.dp))
}

/** O mesmo, na horizontal. */
@Composable
fun VaoH(largura: Int) {
  Spacer(Modifier.width(largura.dp))
}
