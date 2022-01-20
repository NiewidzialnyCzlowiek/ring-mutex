package io.bartlomiejszal

import org.zeromq.ZMQ
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object ZmqSocketExtension {
    fun ZMQ.Socket.send(msg: AppMessage) {
        try {
            this.send(serialize(msg), ZMQ.DONTWAIT)
        } catch (e: Exception) {
            println("Cannot send message $msg. Exception: ${e.message}.")
        }
    }

    fun ZMQ.Socket.receive(): AppMessage? {
        return try {
            deserialize<AppMessage>(this.recv())
        } catch (e: Exception) {
            println("Cannot receive message. Exception ${e.message}.")
            null
        }
    }

    private fun <T> serialize(obj: T?): ByteArray {
        if (obj == null) {
            return ByteArray(0)
        }
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(obj)
        oos.close()
        return baos.toByteArray()
    }

    private fun <T> deserialize(bytes: ByteArray?): T? {
        if (bytes == null || bytes.isEmpty()) {
            return null
        }
        val bais = ByteArrayInputStream(bytes)
        val ois = ObjectInputStream(bais)
        return ois.readObject() as T?
    }
}