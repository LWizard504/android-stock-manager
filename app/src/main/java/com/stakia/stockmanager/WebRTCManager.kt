package com.stakia.stockmanager

import android.content.Context
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class WebRTCManager(private val context: Context) {
    val rootEglBase: EglBase = EglBase.create()
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    
    val peerConnections = mutableMapOf<String, PeerConnection>()
    val remoteVideoTracks = mutableMapOf<String, VideoTrack>()
    
    var localVideoTrack: VideoTrack? = null
    var localAudioTrack: AudioTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
 
    // Vistas para la UI
    val localView = SurfaceViewRenderer(context)
    val remoteViews = mutableMapOf<String, SurfaceViewRenderer>()

    init {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        
        // Inicializar vista local
        localView.init(rootEglBase.eglBaseContext, null)
        localView.setMirror(true)
        localView.setEnableHardwareScaler(true)
    }

    private fun ensureFactory(): PeerConnectionFactory {
        if (peerConnectionFactory == null) {
            val adm = JavaAudioDeviceModule.builder(context)
                .setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .setUseStereoInput(false)
                .setUseStereoOutput(false)
                .createAudioDeviceModule()

            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true 
            
            val encoderFactory = DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .setAudioDeviceModule(adm)
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = false
                    disableNetworkMonitor = true
                })
                .createPeerConnectionFactory()
        }
        return peerConnectionFactory!!
    }

    fun startLocalStreaming(isVideo: Boolean) {
        val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
        am.isMicrophoneMute = false
        am.isSpeakerphoneOn = isVideo
        
        val factory = ensureFactory()
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        localAudioTrack?.setEnabled(true)

        if (isVideo) {
            videoCapturer = createVideoCapturer()
            val videoSource = factory.createVideoSource(false)
            videoCapturer?.initialize(SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext), context, videoSource.capturerObserver)
            videoCapturer?.startCapture(1280, 720, 30)

            localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource)
            localVideoTrack?.addSink(localView)
        }
    }

    fun getOrCreateRemoteView(userId: String): SurfaceViewRenderer {
        val existing = remoteViews[userId]
        if (existing != null) return existing
        
        // Si no existe, lo creamos. IMPORTANTE: Esto debe ser en el UI Thread si se llama desde WebRTC
        val view = SurfaceViewRenderer(context).apply {
            init(rootEglBase.eglBaseContext, null)
            setMirror(false)
            setEnableHardwareScaler(true)
        }
        remoteViews[userId] = view
        return view
    }

    private fun createVideoCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun createPeerConnection(
        remoteUserId: String,
        onIceCandidate: (IceCandidate) -> Unit,
        onTrackAdded: (String) -> Unit
    ): PeerConnection? {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidate(it) }
            }
            override fun onAddStream(stream: MediaStream?) {
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    stream?.videoTracks?.firstOrNull()?.let { track ->
                        val view = getOrCreateRemoteView(remoteUserId)
                        track.addSink(view)
                        remoteVideoTracks[remoteUserId] = track
                    }
                    onTrackAdded(remoteUserId)
                }
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    val track = transceiver?.receiver?.track()
                    if (track is VideoTrack) {
                        val view = getOrCreateRemoteView(remoteUserId)
                        track.addSink(view)
                        remoteVideoTracks[remoteUserId] = track
                    }
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                    }
                    onTrackAdded(remoteUserId)
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                if (p0 == PeerConnection.IceConnectionState.DISCONNECTED || p0 == PeerConnection.IceConnectionState.FAILED) {
                    removePeer(remoteUserId)
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }

        val factory = ensureFactory()
        val pc = factory.createPeerConnection(rtcConfig, observer)
        
        localAudioTrack?.let { pc?.addTrack(it, listOf("ARDAMS")) }
        localVideoTrack?.let { pc?.addTrack(it, listOf("ARDAMS")) }
        
        if (pc != null) peerConnections[remoteUserId] = pc
        return pc
    }
    
    private val iceCandidateQueues = mutableMapOf<String, MutableList<IceCandidate>>()

    fun addIceCandidate(userId: String, candidate: IceCandidate) {
        val pc = peerConnections[userId]
        if (pc?.remoteDescription != null) {
            pc.addIceCandidate(candidate)
        } else {
            iceCandidateQueues.getOrPut(userId) { mutableListOf() }.add(candidate)
        }
    }

    fun processQueuedIceCandidates(userId: String) {
        val pc = peerConnections[userId]
        if (pc?.remoteDescription != null) {
            iceCandidateQueues[userId]?.forEach { pc.addIceCandidate(it) }
            iceCandidateQueues[userId]?.clear()
        }
    }

    fun removePeer(userId: String) {
        peerConnections[userId]?.close()
        peerConnections.remove(userId)
        remoteVideoTracks.remove(userId)
        remoteViews[userId]?.release()
        remoteViews.remove(userId)
        iceCandidateQueues.remove(userId)
    }

    fun toggleSpeakerphone(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    fun stopAll() {
        audioManager.mode = android.media.AudioManager.MODE_NORMAL
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
        localAudioTrack?.dispose()
        localVideoTrack?.dispose()
        localView.release()
        remoteViews.values.forEach { it.release() }
        remoteViews.clear()
        
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        // rootEglBase.release() // Optional, but usually good if we re-init
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    suspend fun createOffer(userId: String): SessionDescription? = suspendCancellableCoroutine { cont ->
        val pc = peerConnections[userId]
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) { cont.resume(sdp) }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { cont.resumeWithException(Exception(error)) }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun createAnswer(userId: String): SessionDescription? = suspendCancellableCoroutine { cont ->
        val pc = peerConnections[userId]
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) { cont.resume(sdp) }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { cont.resumeWithException(Exception(error)) }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun setLocalDescription(userId: String, sdp: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        val pc = peerConnections[userId]
        pc?.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) { cont.resumeWithException(Exception(error)) }
        }, sdp)
    }

    suspend fun setRemoteDescription(userId: String, sdp: SessionDescription) = suspendCancellableCoroutine<Unit> { cont ->
        val pc = peerConnections[userId]
        pc?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(error: String?) { cont.resumeWithException(Exception(error)) }
        }, sdp)
    }
}
