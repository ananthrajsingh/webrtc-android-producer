package me.ananth.webrtc_producer

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignallingClientListener {
    fun onConnectionEstablished()
    fun onOfferReceived(description: SessionDescription)
    fun onIceCandidateReceived(iceCandidate: IceCandidate)
}