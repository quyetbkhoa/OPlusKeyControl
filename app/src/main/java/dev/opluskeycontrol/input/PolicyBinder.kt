package dev.opluskeycontrol.input

import android.os.IBinder
import android.os.Parcel

/** Private service protocol. Calls run on IPC workers, never the input thread. */
object PolicyBinder {
    const val DESCRIPTOR = "dev.opluskeycontrol.Policy.v4"
    const val HELLO = IBinder.FIRST_CALL_TRANSACTION
    const val CONFIG = HELLO + 1
    const val ACTION = HELLO + 2
    const val DISCONNECT = HELLO + 3
    const val FEATURES = HELLO + 4
    const val PLUS = 1
    const val POWER_DOUBLE = 2
    const val PLUS_VOLUME = 4
    const val POWER_VOLUME = 8
    const val ALL_FEATURES = PLUS or POWER_DOUBLE or PLUS_VOLUME or POWER_VOLUME
    fun send(target: IBinder, code: Int, body: (Parcel) -> Unit) {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken(DESCRIPTOR)
            body(parcel)
            check(target.transact(code, parcel, null, IBinder.FLAG_ONEWAY))
        } finally { parcel.recycle() }
    }
}
