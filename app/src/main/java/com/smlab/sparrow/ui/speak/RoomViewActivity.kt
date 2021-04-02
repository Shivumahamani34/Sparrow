package com.smlab.sparrow.ui.speak

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smlab.sparrow.R
import com.smlab.sparrow.ui.speak.TalkieConstants.API_KEY
import com.smlab.sparrow.ui.speak.TalkieConstants.DOMAIN
import com.smlab.sparrow.ui.speak.TalkiePreferenceHelper.getRoomId
import com.smlab.sparrow.util.audioManager
import io.skyway.Peer.*
import io.skyway.Peer.Browser.Canvas
import io.skyway.Peer.Browser.MediaConstraints
import io.skyway.Peer.Browser.MediaStream
import io.skyway.Peer.Browser.Navigator

/**
 * MainActivity.java
 * ECL WebRTC mesh video-chat sample
 */
class RoomViewActivity : AppCompatActivity() {
    var exitRoom: Button? = null
    var roomName: TextView? = null
    var memberCount: TextView? = null
    var _tvMessage: TextView? = null
    private var _peer: Peer? = null
    private var _localStream: MediaStream? = null
    private var _room: Room? = null
    private var _adapter: RemoteViewAdapter? = null
    private var _strOwnId: String? = null
    private var _handler: Handler? = null
//    private var bluetoothManager: ClassicBluetoothManager? = null
    var useBluetoothHeadset = false
    private var scoStateReceiver: BluetoothScoStateReceiver? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener {
        Log.d(TAG, "onAudioFocusChange: $it")
    }

//    val Context.audioManager get() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        val wnd = window
        wnd.addFlags(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_room_view)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.setTitle(R.string.room_title_settings)
        }
        _handler = Handler(Looper.getMainLooper())
        val activity: Activity = this
        _tvMessage = findViewById(R.id.tvMessage)
        memberCount = findViewById(R.id.member_count)
        roomName = findViewById(R.id.room_name)
        roomName?.text = "Room Name: " + getRoomId()
        val btnShare = findViewById<Button>(R.id.btn_share)
        btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.subject) + getRoomId())
            startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.title_share)))
        }

        if (audioManager.isBluetoothScoAvailableOffCall) {
            scoStateReceiver = BluetoothScoStateReceiver()
            val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            applicationContext.registerReceiver(scoStateReceiver, filter)
        } else {
            Log.e(TAG, "Bluetooth SCO can not be used for audio when not in call !!")
        }

        //Get bletooth manager for bluetooth check device connected and available in connection Talkie
//        bluetoothManager = ClassicBluetoothManager.instance
//        useBluetoothHeadset = (bluetoothManager != null
//                && bluetoothManager!!.isHeadsetDeviceConnected)
        //
        // Initialize Peer
        //
        val option = PeerOption()
        option.key = API_KEY
        option.domain = DOMAIN
        _peer = Peer(this, option)

        //
        // Set Peer event callbacks
        //

        // OPEN
        _peer!!.on(Peer.PeerEventEnum.OPEN) { `object` ->
            // Show my ID
            _strOwnId = `object` as String
            val tvOwnId = findViewById<TextView>(R.id.room_id_text)
            tvOwnId.text = "Peer ID : $_strOwnId"

            // Request permissions
            if (ContextCompat.checkSelfPermission(activity,
                            Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(activity,
                            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 0)
            } else {
                // Get a local MediaStream & show it
                startLocalStream()
            }
        }
        _peer!!.on(Peer.PeerEventEnum.CLOSE) { Log.d(TAG, "[On/Close]") }
        _peer!!.on(Peer.PeerEventEnum.DISCONNECTED) { Log.d(TAG, "[On/Disconnected]") }
        _peer!!.on(Peer.PeerEventEnum.ERROR) { `object` ->
            val error = `object` as PeerError
            Log.d(TAG, "[On/Error]" + error.exception.message)
        }


        //
        // Set GUI event listeners
        //
        val btnAction = findViewById<Button>(R.id.btn_join)
        btnAction.isEnabled = true
        btnAction.setOnClickListener { v ->
            v.isEnabled = false
            if (!_bConnected) {
                // Join room
                    joinRoom()
            } else {
                // Leave room
                leaveRoom()
            }
            v.isEnabled = true
        }

        //
        // Set GridView for Remote Video Stream
        //
        val grdRemote = findViewById<GridView>(R.id.grdRemote)
        if (null != grdRemote) {
            _adapter = RemoteViewAdapter(this)
            grdRemote.adapter = _adapter
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            0 -> {
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocalStream()
                } else {
                    Toast.makeText(this, "Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Disable Sleep and Screen Lock
        val wnd = window
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onResume() {
        // Set volume control stream type to WebRTC audio.
            volumeControlStream = AudioManager.STREAM_VOICE_CALL
        super.onResume()
    }

    override fun onPause() {
        // Set default volume control stream type.
            volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        super.onPause()
    }

    override fun onStop() {
        // Enable Sleep and Screen Lock
        val wnd = window
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        super.onStop()
    }

    override fun onDestroy() {
        stopBluetoothScoIfNeeded()
        scoStateReceiver?.let { applicationContext.unregisterReceiver(it) }
        destroyPeer()
        super.onDestroy()
    }

    //
    // Get a local MediaStream & show it
    //
    fun startLocalStream() {
        Navigator.initialize(_peer)
        val constraints = MediaConstraints()
        _localStream = Navigator.getUserMedia(constraints)
        val canvas = findViewById<Canvas>(R.id.svLocalView)
        _localStream?.addVideoRenderer(canvas, 1)
    }

    //
    // Clean up objects
    //
    private fun destroyPeer() {
        leaveRoom()
        if (null != _localStream) {
            val canvas = findViewById<Canvas>(R.id.svLocalView)
            _localStream!!.removeVideoRenderer(canvas, 1)
            _localStream!!.close()
        }
        Navigator.terminate()
        if (null != _peer) {
            unsetPeerCallback(_peer!!)
            if (!_peer!!.isDisconnected) {
                _peer!!.disconnect()
            }
            if (!_peer!!.isDestroyed) {
                _peer!!.destroy()
            }
            _peer = null
        }
    }

    //
    // Unset callbacks for PeerEvents
    //
    fun unsetPeerCallback(peer: Peer) {
        if (null == _peer) {
            return
        }
        peer.on(Peer.PeerEventEnum.OPEN, null)
        peer.on(Peer.PeerEventEnum.CONNECTION, null)
        peer.on(Peer.PeerEventEnum.CALL, null)
        peer.on(Peer.PeerEventEnum.CLOSE, null)
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null)
        peer.on(Peer.PeerEventEnum.ERROR, null)
    }

    //
    // Join the room
    //
    fun joinRoom() {
        if (null == _peer || null == _strOwnId || 0 == _strOwnId!!.length) {
            Toast.makeText(this, "Your PeerID is null or invalid.", Toast.LENGTH_SHORT).show()
            return
        }

        // Get room name
        val roomName = getRoomId()
        if (TextUtils.isEmpty(roomName)) {
            Toast.makeText(this, "You should input room name.", Toast.LENGTH_SHORT).show()
            return
        }

        requestAudioFocus()

        if (audioManager.isBluetoothScoOn) {
            Log.d(TAG, "startStreaming")
            val option = RoomOption()
            option.mode = RoomOption.RoomModeEnum.MESH
            option.stream = _localStream
            audioManager.isMicrophoneMute=true
            //audioManager.isBluetoothScoOn = false
            // Join Room
            _room = _peer!!.joinRoom(roomName, option)
            _bConnected = true

            //
            // Set Callbacks
            //
            _room?.on(Room.RoomEventEnum.OPEN, OnCallback { `object` ->
                if (`object` !is String) return@OnCallback
                val roomName = `object`
                Log.i(TAG, "Enter Room: $roomName")
                memberCount!!.text = "1"
                Toast.makeText(this@RoomViewActivity, "Enter Room: $roomName", Toast.LENGTH_SHORT).show()
            })
            _room?.on(Room.RoomEventEnum.CLOSE, OnCallback { `object` ->
                val roomName = `object` as String
                Log.i(TAG, "Leave Room: $roomName")
                Toast.makeText(this@RoomViewActivity, "Leave Room: $roomName", Toast.LENGTH_LONG).show()

                // Remove all streams
                _adapter!!.removeAllRenderers()

                // Unset callbacks
                _room?.on(Room.RoomEventEnum.OPEN, null)
                _room?.on(Room.RoomEventEnum.CLOSE, null)
                _room?.on(Room.RoomEventEnum.ERROR, null)
                _room?.on(Room.RoomEventEnum.PEER_JOIN, null)
                _room?.on(Room.RoomEventEnum.PEER_LEAVE, null)
                _room?.on(Room.RoomEventEnum.STREAM, null)
                _room?.on(Room.RoomEventEnum.REMOVE_STREAM, null)
                _room = null
                _bConnected = false
                updateActionButtonTitle()
            })
            _room?.on(Room.RoomEventEnum.ERROR, OnCallback { `object` ->
                val error = `object` as PeerError
                Log.d(TAG, "RoomEventEnum.ERROR:$error")
            })
            _room?.on(Room.RoomEventEnum.PEER_JOIN, OnCallback { `object` ->
                Log.d(TAG, "RoomEventEnum.PEER_JOIN:")
                if (`object` !is String) return@OnCallback
                val peerId = `object`
                Log.i(TAG, "Join Room: $peerId")
                Toast.makeText(this@RoomViewActivity, "$peerId has joined.", Toast.LENGTH_LONG).show()
            })
            _room?.on(Room.RoomEventEnum.PEER_LEAVE, OnCallback { `object` ->
                Log.d(TAG, "RoomEventEnum.PEER_LEAVE:")
                if (`object` !is String) return@OnCallback
                val peerId = `object`
                Log.i(TAG, "Leave Room: $peerId")
                Toast.makeText(this@RoomViewActivity, "$peerId has left.", Toast.LENGTH_LONG).show()
                _adapter!!.remove(peerId)
                val count = memberCount!!.text.toString().toInt()
                memberCount!!.text = (count - 1).toString()
            })
            _room?.on(Room.RoomEventEnum.STREAM, OnCallback { `object` ->
                Log.d(TAG, "RoomEventEnum.STREAM: + $`object`")
                if (`object` !is MediaStream) return@OnCallback
                val stream = `object`
                Log.d(TAG, "peer = " + stream.peerId + ", label = " + stream.label)
                _adapter!!.add(stream)
                memberCount!!.text = (_adapter!!.count + 1).toString()
            })
            _room?.on(Room.RoomEventEnum.REMOVE_STREAM, OnCallback { `object` ->
                Log.d(TAG, "RoomEventEnum.REMOVE_STREAM: $`object`")
                if (`object` !is MediaStream) return@OnCallback
                val stream = `object`
                Log.d(TAG, "peer = " + stream.peerId + ", label = " + stream.label)
                _adapter!!.remove(stream)
                memberCount!!.text = _adapter!!.count.toString()
            })
        } else {
            startBluetoothScoIfNeeded()
        }

        // Update UI
        updateActionButtonTitle()
    }

    private fun startBluetoothScoIfNeeded(): Boolean {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (!audioManager.isBluetoothScoOn) {
            Log.d(TAG, "startBluetoothSco")
            audioManager.startBluetoothSco()
            return true
        }
        return false
    }

    private fun stopBluetoothScoIfNeeded() {
        audioManager.mode = AudioManager.MODE_NORMAL
        if (audioManager.isBluetoothScoOn) {
            Log.d(TAG, "stopBluetoothSco")
            audioManager.stopBluetoothSco()
        }
    }

    //
    // Leave the room
    //
    fun leaveRoom() {
        if (null == _peer || null == _room) {
            return
        }
        memberCount!!.text = "0"
        _room!!.close()
        onBackPressed()
    }

    //
    // Update actionButton title
    //
    fun updateActionButtonTitle() {
        _handler!!.post {
            val btnAction = findViewById<Button>(R.id.btn_join)
            if (null != btnAction) {
                if (!_bConnected) {
                    btnAction.text = "Join Room"
                } else {
                    btnAction.text = "Leave Room"
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (null == _peer || null == _room) {
            return super.onSupportNavigateUp()
        }
        memberCount!!.text = "0"
        _room!!.close()
        onBackPressed()
        return super.onSupportNavigateUp()
    }

    private fun requestAudioFocus(): Boolean {
        Log.d(TAG, "requestAudioFocus")
        var playbackNowAuthorized = false

        if (Build.VERSION.SDK_INT < 26) {
            val result = audioManager.requestAudioFocus(
                    afChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "requestAudioFocus AUDIOFOCUS_REQUEST_GRANTED")
                playbackNowAuthorized = true
            } else {
                Log.i(TAG, "requestAudioFocus AUDIOFOCUS_REQUEST_FAILED")
            }
        } else {
            audioFocusRequest = AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(afChangeListener, Handler())
                    .build()
            val focusLock = Any()
            audioFocusRequest?.let {
                val result = audioManager.requestAudioFocus(it)
                synchronized(focusLock) {
                    playbackNowAuthorized = when (result) {
                        AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                            Log.i(TAG, "requestAudioFocus AUDIOFOCUS_REQUEST_FAILED")
                            false
                        }
                        AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                            Log.i(TAG, "requestAudioFocus AUDIOFOCUS_REQUEST_GRANTED")
                            true
                        }
                        AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                            Log.i(TAG, "requestAudioFocus AUDIOFOCUS_REQUEST_DELAYED")
                            false
                        }
                        else -> {
                            Log.i(TAG, "requestAudioFocus AUDIOFOCUS_REQUEST_ELSE")
                            false
                        }
                    }
                }
            }
        }
        return playbackNowAuthorized
    }

    private inner class BluetoothScoStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action !== AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                return
            }
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    Log.i(TAG, "SCO_AUDIO_STATE_CONNECTED")
                    audioManager.isBluetoothScoOn = true
                }
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                    Log.i(TAG, "SCO_AUDIO_STATE_CONNECTING")
                }
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    Log.i(TAG, "SCO_AUDIO_STATE_DISCONNECTED")
                    audioManager.isBluetoothScoOn = false
                }
                else -> {
                    Log.e(TAG, "SCO_AUDIO_STATE_ERROR")
                }
            }

        }
    }

    companion object {
        private val TAG = RoomViewActivity::class.java.simpleName
        var _bConnected = false
        var instance: RoomViewActivity? = null
    }
}