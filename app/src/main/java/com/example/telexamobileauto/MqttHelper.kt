package com.example.telexamobileauto

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

interface ConnectionStatusCallback {
    fun onConnectionStatusChanged(connected: Boolean, message: String)
}

class MqttHelper(
    private val context: Context,
    private val onMessage: (String) -> Unit
) {
    private val serverUri = "tcp://telexa.co.in:1883"
    private val clientId = MqttClient.generateClientId()
    private val username = "telexa"                // fixed
    private val password = "testpassword"         // fixed

    private var mqttClient: MqttClient? = null
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    private var connectionCallback: ConnectionStatusCallback? = null

    // Topic is dynamic
    private var currentSubscribeTopic: String = "defaultTopic"

    fun setConnectionStatusCallback(callback: ConnectionStatusCallback) {
        this.connectionCallback = callback
    }

    fun connect(topic: String) {
        try {
            currentSubscribeTopic = topic  // set user/device topic at connect time

            mqttClient = MqttClient(serverUri, clientId, null)
            val options = MqttConnectOptions().apply {
                userName = username
                password = this@MqttHelper.password.toCharArray()
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 20
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d("MQTT", "Connection lost: ${cause?.message}")
                    connectionCallback?.onConnectionStatusChanged(false, "Connection lost: ${cause?.message ?: "Unknown error"}")
                    executor.schedule({ connect(currentSubscribeTopic) }, 5, TimeUnit.SECONDS)
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val msg = "[$topic] ${message?.toString() ?: "null message"}"
                    Log.d("MQTT", msg)
                    onMessage(msg)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    Log.d("MQTT", "Message delivered")
                }
            })

            mqttClient?.connect(options)
            Log.d("MQTT", "Connected to broker")
            connectionCallback?.onConnectionStatusChanged(true, "Connected to $serverUri")
            subscribe(currentSubscribeTopic)

        } catch (e: Exception) {
            Log.e("MQTT", "Exception during connect: ${e.message}")
            connectionCallback?.onConnectionStatusChanged(false, "Connection failed: ${e.message}")
        }
    }

    fun publish(msg: String) {
        if (mqttClient?.isConnected != true) return
        try {
            val message = MqttMessage(msg.toByteArray())
            mqttClient?.publish(currentSubscribeTopic, message)
            Log.d("MQTT", "Published message: $msg")
        } catch (e: Exception) {
            Log.e("MQTT", "Exception during publish: ${e.message}")
        }
    }

    private fun subscribe(topic: String) {
        if (mqttClient?.isConnected != true) return
        try {
            mqttClient?.subscribe(topic, 1)
            Log.d("MQTT", "Subscribed to $topic")
        } catch (e: Exception) {
            Log.e("MQTT", "Exception during subscribe: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            executor.shutdown()
            Log.d("MQTT", "Disconnected")
            connectionCallback?.onConnectionStatusChanged(false, "Disconnected")
        } catch (e: Exception) {
            Log.e("MQTT", "Exception during disconnect: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }
}
