package io.bartlomiejszal

import com.sksamuel.hoplite.ConfigLoader
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.*
import kotlin.concurrent.thread

data class Config(
    val initiator: Boolean = false,
    val address: String = "127.0.0.1:8090",
    val followerAddress: String
)

enum class Color {
    NONE,
    WHITE,
    BLACK;

    companion object {
        fun flip(color: Color): Color = when (color) {
            NONE -> NONE
            WHITE -> BLACK
            BLACK -> WHITE
        }
    }
}

data class PeerState(
    val predecessorColor: Color = Color.NONE,
    val color: Color = Color.NONE,
    val retransmitToken: Boolean = false,
    val holdingToken: Boolean = false
)

data class AppData(
    val color: Color,
    val message: Serializable? = null
) : Serializable

enum class MessageType {
    TOKEN,
    TOKEN_ACK
}

data class AppMessage(
    val type: MessageType,
    val data: AppData
) : Serializable

class Peer(config: Config) {
    private val zContext = ZContext()
    private val predecessorSocket = zContext.createSocket(SocketType.REP)
    private val followerSocket = zContext.createSocket(SocketType.REQ)
    private var runListener: Boolean = true
    private var retransmitToken: Boolean = false
    private val listener: Thread

    private var state: PeerState

    init {
        predecessorSocket.bind("tcp://${config.address}")
        followerSocket.connect("tcp://${config.followerAddress}")
        followerSocket.sendTimeOut = 0
        followerSocket.receiveTimeOut = 0
        predecessorSocket.sendTimeOut = 0

        state = PeerState()
        listener = thread { listenerLoop() }
    }

    fun passToken() {
        println("Passing token to the next peer")
        state = state.copy(holdingToken = false)
        val token = AppMessage(MessageType.TOKEN, AppData(state.color))
        retransmitToken = true
        while (retransmitToken) {
            followerSocket.send(token)
            val ack = followerSocket.receive()
            if (ack != null && ack.type == MessageType.TOKEN_ACK && ack.data.color == state.color) {
                retransmitToken = false
            }
            Thread.sleep(10)
        }
    }

    fun ownsToken() = state.holdingToken

    fun work() {
        if (!state.holdingToken) {
            throw IllegalStateException("Cannot perform critical section - not holding the token")
        }
        println("Executing critical section tasks")
        Thread.sleep(1000)
    }

    fun setInitiator() {
        state = state.copy(color = Color.WHITE, holdingToken = true)
    }

    private fun listenerLoop() {
        while (runListener) {
            val message = predecessorSocket.receive()
            when (message?.type) {
                MessageType.TOKEN -> handleToken(message, predecessorSocket)
                else -> throw IllegalArgumentException("Unhandled message: $message")
            }
        }
    }

    private fun handleToken(message: AppMessage, sender: ZMQ.Socket) {
        val tokenColor = message.data.color
        println("Received token with color $tokenColor")
        val ack = AppMessage(MessageType.TOKEN_ACK, AppData(tokenColor))
        sender.send(ack)

        if (tokenColor != state.predecessorColor) {
            retransmitToken = false
            state = state.copy(predecessorColor = tokenColor, holdingToken = true)
            if (state.color != tokenColor) {
                state = state.copy(color = tokenColor)
            } else {
                state = state.copy(color = Color.flip(state.color))
            }
            println("Current state: $state")
        }
    }

    private fun ZMQ.Socket.send(msg: AppMessage) {
        try {
            this.send(serialize(msg), ZMQ.DONTWAIT)
        } catch (e: Exception) {
            println("Cannot send message $msg. Exception: ${e.message}.")
        }
    }

    private fun ZMQ.Socket.receive(): AppMessage? {
        return try {
            deserialize<AppMessage>(this.recv())
        } catch (e: Exception) {
            println("Cannot receive message. Exception ${e.message}.")
            null
        }
    }


    companion object {
        fun <T> serialize(obj: T?): ByteArray {
            if (obj == null) {
                return ByteArray(0)
            }
            val baos = ByteArrayOutputStream()
            val oos = ObjectOutputStream(baos)
            oos.writeObject(obj)
            oos.close()
            return baos.toByteArray()
        }

        fun <T> deserialize(bytes: ByteArray?): T? {
            if (bytes == null || bytes.isEmpty()) {
                return null
            }
            val bais = ByteArrayInputStream(bytes)
            val ois = ObjectInputStream(bais)
            return ois.readObject() as T?
        }
    }
}

object App {
    @JvmStatic fun main(args: Array<String>) {
        println("[SWN] - mutual exclusion algorithm in bidirectional ring topology")
        val config = ConfigLoader().loadConfigOrThrow<Config>("/config.yaml")
        println("Config parsed successfully: $config")
        println("Is this node the initiator: ${config.initiator}")
        val peer = Peer(config)
        if (config.initiator) {
            println("Initiating sequence")
            peer.setInitiator()
            peer.work()
            peer.passToken()
        }
        for (i in 1..10) {
            println("Waiting to get token")
            while (!peer.ownsToken()) {
                Thread.sleep(100)
            }
            println("Epoch $i")
            peer.work()
            peer.passToken()
        }
    }
}