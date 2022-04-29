package dev.ajkneisl.printercontroller

import dev.ajkneisl.printerlib.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.network.sockets.*
import io.ktor.serialization.*
import io.ktor.serialization.kotlinx.*
import io.ktor.websocket.*
import java.util.*
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Socket {
    private val LOGGER: Logger = LoggerFactory.getLogger(this.javaClass)

    /** If the [CLIENT] is currently connected. */
    var IS_CONNECTED: Boolean = false

    /** The amount of retried attempts to connect to backend. Doesn't exceed 10. */
    var RETRY_ATTEMPTS: Int = 0

    /** Client to interact with printer-backend. */
    private val CLIENT =
        HttpClient(CIO) {
            install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
        }

    /** Hook to the socket. */
    private suspend fun hookSocket() {
        CLIENT.webSocket(host = "localhost", port = 8010, path = "/watch") {
            LOGGER.info("Successfully connected to socket.")
            RETRY_ATTEMPTS = 0

            for (frame in incoming) {
                LOGGER.debug("Incoming: ${(incoming.receive() as? Frame.Text)?.readText()}")

                val incoming: SocketMessage =
                    try {
                        receiveDeserialized()
                    } catch (ex: WebsocketDeserializeException) {
                        sendSerialized(ErrorResponse("Invalid serialization.") as SocketMessage)
                        continue
                    }

                when (incoming) {
                    is SocketRequest -> {
                        when (incoming) {
                            is PrintRequest -> {
                                Controller.print(incoming.payload)
                            }
                            is RequestAuthentication ->
                                sendSerialized(Authenticate("abc", "PRINTER-1") as SocketMessage)
                            else -> LOGGER.error("Invalid request.")
                        }
                    }
                    is SocketResponse -> {
                        when (incoming) {
                            is SuccessResponse -> LOGGER.info(incoming.message)
                            is ErrorResponse -> LOGGER.error(incoming.message)
                        }
                    }
                }
            }
        }
    }

    /** Connect to the hook and automatically attempt to reconnect. */
    suspend fun connectHook() {
        try {
            RETRY_ATTEMPTS++
            IS_CONNECTED = true
            hookSocket()
        } catch (ex: Exception) {
            IS_CONNECTED = false
            LOGGER.error("FATAL: The socket has disconnected.")
            retryHook()
        }
    }

    /** Retry connecting to webhook. Give up after 10 failed attempts. */
    private suspend fun retryHook() {
        if (RETRY_ATTEMPTS > 10) {
            LOGGER.error("FATAL: Retry attempts exceeded 10, giving up.")
            quitNotification()
            exitProcess(16)
        }

        LOGGER.info("Retrying connection to hook in five seconds.")

        delay(5000)

        LOGGER.info("Retrying connection to hook... ($RETRY_ATTEMPTS)")
        connectHook()
    }

    /** The notification when giving up connecting. */
    private fun quitNotification() {
        Controller.print(
            Print(
                "FATAL-DISCON",
                System.currentTimeMillis(),
                listOf(
                    PrintText(PrintDefaults.TITLE, 0, "FATAL ERROR"),
                    PrintText(
                        PrintDefaults.DEFAULT,
                        0,
                        "There was an issue connecting to the information socket! :("
                    )
                )
            )
        )
    }
}
