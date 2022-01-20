package io.bartlomiejszal

import com.sksamuel.hoplite.ConfigLoader
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.*
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.random.Random

data class Config(
    val initiator: Boolean = false,
    val address: String = "127.0.0.1:8090",
    val followerAddress: String,
    val ackOmissionRate: Float = 0.5f
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

class Peer(val config: Config) {
    private val zContext = ZContext()
    private val predecessorSocket = zContext.createSocket(SocketType.REP)
    private val followerSocket = zContext.createSocket(SocketType.REQ)
    private var runListener: Boolean = true
    private var retransmitToken: Boolean = false
    private val listener: Thread
    private var tokenRetransmitter: Thread? = null
    private val random = Random(Instant.now().toEpochMilli())

    private var state: PeerState

    init {
        predecessorSocket.bind("tcp://${config.address}")
        followerSocket.connect("tcp://${config.followerAddress}")

        state = PeerState()
        listener = thread { listenerLoop() }
    }

    fun passToken() {
        println("Passing token to the next peer")
        synchronized(this) {
            state = state.copy(holdingToken = false)
        }
        val token = AppMessage(MessageType.TOKEN, AppData(state.color))
        followerSocket.send(token)
        val ack = followerSocket.receive()
        if (ack == null || ack.type != MessageType.TOKEN_ACK || ack.data.color != state.color) {
            tokenRetransmitter = thread { tokenRetransmitterLoop(token) }
        }
    }

    fun ownsToken() = synchronized(this) { state.holdingToken }

    fun work() {
        if (!state.holdingToken) {
            throw IllegalStateException("Cannot perform critical section - not holding the token")
        }
        println("Executing critical section tasks")
        Thread.sleep(5000)
    }

    fun setInitiator() {
        state = state.copy(color = Color.WHITE, holdingToken = true)
    }

    private fun listenerLoop() {
        while (runListener) {
            val message = predecessorSocket.receive()
            if (message?.type == MessageType.TOKEN) {
                println("Received token: $message")
                val tokenColor = message.data.color

                sendAck(predecessorSocket, tokenColor)

                if (tokenColor != state.predecessorColor) {
                    stopTokenRetransmission()
                    val newColor = if (state.color != tokenColor) {
                        tokenColor
                    } else {
                        Color.flip(state.color)
                    }
                    synchronized(this) {
                        state = state.copy(
                            color = newColor,
                            predecessorColor = tokenColor,
                            holdingToken = true)
                    }
                    println("Current state: $state")
                }
            }
        }
    }

    private fun sendAck(ackReceiver: ZMQ.Socket, tokenColor: Color) {
        if (random.nextFloat() >= config.ackOmissionRate) {
            println("Sending ack")
            val ack = AppMessage(MessageType.TOKEN_ACK, AppData(tokenColor))
            ackReceiver.send(ack)
        } else {
            println("Omitting ack")
            ackReceiver.send(ByteArray(0))
        }
    }

    private fun tokenRetransmitterLoop(token: AppMessage) {
        retransmitToken = true
        while (retransmitToken) {
            println("Retransmitting token")
            followerSocket.send(token)
            val ack = followerSocket.receive()
            if (ack != null && ack.type == MessageType.TOKEN_ACK && ack.data.color == state.color) {
                retransmitToken = false
            }
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                retransmitToken = false
            }
        }
    }

    private fun stopTokenRetransmission() {
        retransmitToken = false
        tokenRetransmitter?.join()
        tokenRetransmitter = null
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

        var epoch = 1
        val peer = Peer(config)
        if (config.initiator) {
            println("Initiating sequence")
            peer.setInitiator()
            peer.work()
            peer.passToken()
            epoch += 1
        }
        while(true) {
            println("Waiting to get token")
            while (!peer.ownsToken()) {
                Thread.sleep(1000)
            }
            println("Epoch $epoch")
            peer.work()
            peer.passToken()
            epoch += 1
        }
    }
}
