package net.syncthing.java.bep.connectionactor

import com.google.common.truth.Truth.assertThat
import net.syncthing.java.bep.BlockExchangeProtos
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.*
import java.util.stream.Stream

class HelloMessageHandlerTest {

    /**
     * @param wireContent The data seen on the wire when sending the [hello] message.
     */
    @ParameterizedTest
    @MethodSource("helloMessages")
    fun correctlyFormatsOutboundPreAuthenticationMessage(hello: BlockExchangeProtos.Hello, wireContent: ByteArray) {
      val output = ByteArrayOutputStream()

      // The stream must be buffered, because sockets are, and we want to ensure that the function flushes the stream.
      sendPreAuthenticationMessage(hello, DataOutputStream(output.buffered()))
      assertThat(output.toByteArray()).isEqualTo(wireContent)
    }

    /**
     * @param wireContent The data seen on the wire when sending the [hello] message.
     */
    @ParameterizedTest
    @MethodSource("helloMessages")
    fun correctlyReceivesInboundPreAuthenticationMessage(hello: BlockExchangeProtos.Hello, wireContent: ByteArray) {

      // The stream must be buffered, because sockets are
      val input = wireContent.inputStream().buffered()

      val actual = receivePreAuthenticationMessage(DataInputStream(input))
      assertThat(actual).isEqualTo(hello)
    }

    companion object {
        private val base64 = Base64.getDecoder()

        @JvmStatic
        fun helloMessages(): Stream<Arguments> = Stream.of(
                arguments(BlockExchangeProtos.Hello.newBuilder()
                        .setClientName("Java")
                        .setClientVersion("8")
                        .setDeviceName("test device")
                        .build(), base64.decode("LqfZCwAWCgt0ZXN0IGRldmljZRIESmF2YRoBOA==")),
                arguments(BlockExchangeProtos.Hello.newBuilder()
                        .setClientName("Go")
                        .setClientVersion("1.3.4")
                        .setDeviceName("MacBook Pro")
                        .build(), base64.decode("LqfZCwAYCgtNYWNCb29rIFBybxICR28aBTEuMy40"))
            )
      }
}