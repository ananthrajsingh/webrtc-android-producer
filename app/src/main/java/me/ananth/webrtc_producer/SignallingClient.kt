package me.ananth.webrtc_producer

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.GsonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readText
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

/**
 * Used to talk to Signalling Server. The signalling server informs the application regarding
 * and call request. This also initiates the call
 */
@ExperimentalCoroutinesApi
@KtorExperimentalAPI
class SignallingClient(
    private val listener: SignallingClientListener
) : CoroutineScope {

    companion object {
        private const val HOST_ADDRESS = "35.200.206.129"
    }

    private val job = Job()

    private val gson = Gson()

    override val coroutineContext = Dispatchers.IO + job

    /* Asynchronous ktor http client supporting WebSockets */
    private val client = HttpClient(CIO) {

        install(WebSockets) // Ktor web socket

        install(JsonFeature) {
            /* For serialising and de-serialising requests and response */
            serializer = GsonSerializer()
        }
    }

    /* Coroutines channel */
    private val sendChannel = ConflatedBroadcastChannel<String>()

    init {
        Log.v(this@SignallingClient.javaClass.simpleName, "Connect: call")
        connect()
    }

    /**
     * [launch] launches a new coroutine without blocking the thread.
     * Inside the coroutine, the Ktor client does the work
     */
    private fun connect() = launch {
        Log.v(this@SignallingClient.javaClass.simpleName, "Connect: launch")
        client.ws(host = HOST_ADDRESS, port = 8080, path = "/connect") {
            listener.onConnectionEstablished()
            val sendData = sendChannel.openSubscription()
            try {
                while (true) {

//                    Log.v(this@SignallingClient.javaClass.simpleName, "Connect: while loop sendData.isEmpty(): ${sendData.isEmpty}")
                    sendData.poll()?.let {
                        Log.v(this@SignallingClient.javaClass.simpleName, "Connect: Sending : $it")
                        outgoing.send(Frame.Text(it))
                    }
                    incoming.poll()?.let { frame ->
                        Log.v(this@SignallingClient.javaClass.simpleName, "Connect: Incoming: ${frame.buffer.toString()}")
                        if (frame is Frame.Text) {
                            val data = frame.readText()
                            Log.v(this@SignallingClient.javaClass.simpleName, "Received: $data")
                            val jsonObject = gson.fromJson(data, JsonObject::class.java)
                            withContext(Dispatchers.Main) {
                                if (jsonObject.has("serverUrl")) {
                                    listener.onIceCandidateReceived(gson.fromJson(jsonObject, IceCandidate::class.java))
                                } else if (jsonObject.has("type") && jsonObject.get("type").asString == "OFFER") {
                                    listener.onOfferReceived(gson.fromJson(jsonObject, SessionDescription::class.java))
                                } else if (jsonObject.has("type") && jsonObject.get("type").asString == "ANSWER") {
                                    // Do nothing
                                }
                            }
                        }
                    }
                }
            } catch (exception: Throwable) {
                Log.e("asd","asd",exception)
            }
        }
    }

    fun send(dataObject: Any?) = runBlocking {
        sendChannel.send(gson.toJson(dataObject))
    }

    fun destroy() {
        client.close()
        job.complete()
    }
}