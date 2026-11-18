plugins {
  // O AGP 9 ja traz o Kotlin embutido: aplicar o kotlin.android por cima
  // briga com ele pela extensao "kotlin". So o plugin do Compose entra aqui.
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
