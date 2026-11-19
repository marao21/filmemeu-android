package com.filmemeu.app.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import okhttp3.Call
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * DataSource do Media3 apoiado no OkHttpClient do aplicativo.
 *
 * Usar o mesmo cliente HTTP que valida as fontes garante HTTP/2, TLS,
 * redirecionamentos e cabecalhos (User-Agent, Referer, Origin) identicos
 * entre a checagem da playlist e a reproducao no ExoPlayer.
 */
@OptIn(UnstableApi::class)
class OkHttpDataSource(
  private val callFactory: Call.Factory,
  private val headersProvider: () -> Map<String, String>
) : BaseDataSource(/* isNetwork = */ true) {

  class Factory(
    private val callFactory: Call.Factory,
    private val headersProvider: () -> Map<String, String>
  ) : DataSource.Factory {
    override fun createDataSource(): DataSource = OkHttpDataSource(callFactory, headersProvider)
  }

  private var dataSpec: DataSpec? = null
  private var response: Response? = null
  private var responseByteStream: InputStream? = null
  private var opened = false
  private var bytesToRead: Long = 0
  private var bytesRead: Long = 0

  override fun getUri(): Uri? =
    response?.request?.url?.toString()?.let(Uri::parse) ?: dataSpec?.uri

  override fun getResponseHeaders(): Map<String, List<String>> =
    response?.headers?.toMultimap() ?: emptyMap()

  override fun open(dataSpec: DataSpec): Long {
    this.dataSpec = dataSpec
    bytesRead = 0
    bytesToRead = 0
    transferInitializing(dataSpec)

    val request = makeRequest(dataSpec)
    val resp = try {
      callFactory.newCall(request).execute()
    } catch (e: IOException) {
      throw HttpDataSource.HttpDataSourceException.createForIOException(
        e,
        dataSpec,
        HttpDataSource.HttpDataSourceException.TYPE_OPEN
      )
    }
    response = resp

    val responseCode = resp.code
    if (!resp.isSuccessful) {
      val headers = resp.headers.toMultimap()
      val message = resp.message
      val errorBody = try {
        resp.body?.bytes() ?: ByteArray(0)
      } catch (_: IOException) {
        ByteArray(0)
      }
      closeConnectionQuietly()
      throw HttpDataSource.InvalidResponseCodeException(
        responseCode,
        message,
        null,
        headers,
        dataSpec,
        errorBody
      )
    }

    val body = resp.body ?: run {
      closeConnectionQuietly()
      throw HttpDataSource.HttpDataSourceException(
        "Resposta vazia do servidor",
        dataSpec,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        HttpDataSource.HttpDataSourceException.TYPE_OPEN
      )
    }

    val contentLength = body.contentLength()
    val bytesToSkip = if (responseCode == 200 && dataSpec.position != 0L) dataSpec.position else 0L

    bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
      dataSpec.length
    } else if (contentLength != -1L) {
      (contentLength - bytesToSkip).coerceAtLeast(0L)
    } else {
      C.LENGTH_UNSET.toLong()
    }

    responseByteStream = body.byteStream()
    opened = true
    transferStarted(dataSpec)

    if (bytesToSkip > 0L) {
      try {
        skipFully(bytesToSkip)
      } catch (e: IOException) {
        close()
        throw HttpDataSource.HttpDataSourceException.createForIOException(
          e,
          dataSpec,
          HttpDataSource.HttpDataSourceException.TYPE_OPEN
        )
      }
    }

    return bytesToRead
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    val stream = responseByteStream ?: return C.RESULT_END_OF_INPUT

    var readLength = length
    if (bytesToRead != C.LENGTH_UNSET.toLong()) {
      val remaining = bytesToRead - bytesRead
      if (remaining == 0L) return C.RESULT_END_OF_INPUT
      readLength = minOf(length.toLong(), remaining).toInt()
    }

    val read = try {
      stream.read(buffer, offset, readLength)
    } catch (e: IOException) {
      val spec = dataSpec
      if (spec != null) {
        throw HttpDataSource.HttpDataSourceException.createForIOException(
          e,
          spec,
          HttpDataSource.HttpDataSourceException.TYPE_READ
        )
      }
      throw e
    }

    if (read == -1) return C.RESULT_END_OF_INPUT
    bytesRead += read
    bytesTransferred(read)
    return read
  }

  override fun close() {
    if (opened) {
      opened = false
      transferEnded()
    }
    closeConnectionQuietly()
    dataSpec = null
  }

  private fun makeRequest(dataSpec: DataSpec): Request {
    val builder = Request.Builder().url(dataSpec.uri.toString())

    for ((k, v) in headersProvider()) {
      builder.header(k, v)
    }
    for ((k, v) in dataSpec.httpRequestHeaders) {
      builder.header(k, v)
    }

    val position = dataSpec.position
    val length = dataSpec.length
    if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
      val range = StringBuilder("bytes=").append(position).append("-")
      if (length != C.LENGTH_UNSET.toLong()) {
        range.append(position + length - 1)
      }
      builder.header("Range", range.toString())
      if (!dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)) {
        builder.header("Accept-Encoding", "identity")
      }
    }

    val body = when {
      dataSpec.httpBody != null -> dataSpec.httpBody!!.toRequestBody(null)
      dataSpec.httpMethod == DataSpec.HTTP_METHOD_POST -> ByteArray(0).toRequestBody(null)
      else -> null
    }
    builder.method(dataSpec.httpMethodString, body)
    return builder.build()
  }

  private fun skipFully(bytesToSkip: Long) {
    val stream = responseByteStream ?: return
    var remaining = bytesToSkip
    val skipBuf = ByteArray(4096)
    while (remaining > 0) {
      val toRead = minOf(remaining, skipBuf.size.toLong()).toInt()
      val read = stream.read(skipBuf, 0, toRead)
      if (read == -1) throw EOFException("Fim inesperado ao pular bytes")
      remaining -= read
      bytesTransferred(read)
    }
  }

  private fun closeConnectionQuietly() {
    try {
      responseByteStream?.close()
    } catch (_: Exception) {
    }
    responseByteStream = null
    try {
      response?.close()
    } catch (_: Exception) {
    }
    response = null
  }
}
