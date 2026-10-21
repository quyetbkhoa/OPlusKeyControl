package dev.opluskeycontrol.input

import android.os.IBinder
import android.os.Parcel

/** Private service protocol. Calls run on IPC workers, never the input thread. */
object PolicyBinder {
    const val DESCRIPTOR = "dev.opluskeycontrol.Policy.v2"
    const val HELLO = IBinder.FIRST_CALL_TRANSACTION
    const val CONFIG = HELLO + 1
    const val ACTION = HELLO + 2
    const val DISCONNECT = HELLO + 3
    fun send(target: IBinder, code: Int, body: (Parcel) -> Unit) {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken(DESCRIPTOR)
            body(parcel)
            check(target.transact(code, parcel, null, IBinder.FLAG_ONEWAY))
        } finally { parcel.recycle() }
    }
}
