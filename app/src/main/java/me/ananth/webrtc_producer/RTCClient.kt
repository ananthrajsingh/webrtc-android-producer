package me.ananth.webrtc_producer

import android.app.Application
import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.MediaConstraints





class RTCClient(
    context: Application,
    observer: PeerConnection.Observer
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_track"
        private const val AUDIO_TRACK_ID = "audio_local_track"
    }

    private val rootEglBase: EglBase = EglBase.create()

    init {

        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }

    private fun initPeerConnectionFactory(context: Application) {
        Log.e("RTCClient", "initPeerConnectionFactory")
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        Log.e("RTCClient", "buildPeerConnectionFactory")
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) = peerConnectionFactory.createPeerConnection(
        iceServer,
        observer
    )

    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
        videoCapturer.startCapture(320, 240, 60)
        val localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
        localVideoTrack.addSink(localVideoOutput)
        val localStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
        // We start out with an empty MediaStream object,
        // created with help from our PeerConnectionFactory
        // Note that LOCAL_MEDIA_STREAM_ID can be any string
        localStream.addTrack(localVideoTrack)


        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        val localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack.setEnabled(true)
        localStream.addTrack(localAudioTrack)
        peerConnection?.addStream(localStream)
    }

//    private fun PeerConnection.call(sdpObserver: SdpObserver) {
//        val constraints = MediaConstraints().apply {
//            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
//            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
//        }
//
//        Log.e("RTCClient", "Creating Offer")
//        createOffer(object : SdpObserver by sdpObserver {
//            override fun onCreateSuccess(desc: SessionDescription?) {
//                Log.e("RTCClient", "Offer onCreateSuccess description: ${desc.toString()}")
//
//                setLocalDescription(object : SdpObserver {
//                    override fun onSetFailure(p0: String?) {
//                        Log.e("RTCClient", "call onSetFailure")
//                    }
//
//                    override fun onSetSuccess() {
//                        Log.e("RTCClient", "call onSetSuccess")
//                    }
//
//                    override fun onCreateSuccess(p0: SessionDescription?) {
//                        Log.e("RTCClient", "call onCreateSuccess")
//
//                    }
//
//                    override fun onCreateFailure(p0: String?) {
//                        Log.e("RTCClient", "call onCreateFailure")
//
//                    }
//                }, desc)
//                sdpObserver.onCreateSuccess(desc)
//            }
//        }, constraints)
//    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        Log.e("RTCClient", "Creating Answer")
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.e("RTCClient", "Answer onCreateSuccess description: ${p0.toString()}")
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                        Log.e("RTCClient", "answer onSetFailure")
                    }

                    override fun onSetSuccess() {
                        Log.e("RTCClient", "answer onSetSuccess")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                        Log.e("RTCClient", "answer onCreateSuccess")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e("RTCClient", "answer onCreateFailure")
                    }
                }, p0)
                sdpObserver.onCreateSuccess(p0)
            }
        }, constraints)
    }

//    fun call(sdpObserver: SdpObserver) = peerConnection?.call(sdpObserver)

    fun answer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        Log.e("RTCClient", "onRemoteSessionReceived")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
                Log.e("RTCClient", "answer onSetFailure $p0")
            }

            override fun onSetSuccess() {
                Log.e("RTCClient", "answer onSetSuccess")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
                Log.e("RTCClient", "answer onCreateSuccess ${p0.toString()}")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e("RTCClient", "answer onCreateFailure $p0")
            }
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        Log.e("RTCClient", "addIceCandidate sdp: ${iceCandidate?.sdp}")
        peerConnection?.addIceCandidate(iceCandidate)
    }
}