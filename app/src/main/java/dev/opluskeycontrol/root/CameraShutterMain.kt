package dev.opluskeycontrol.root

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import kotlin.system.exitProcess

/** One pair, explicitly targeted at the camera UID. Never falls back to untargeted
 * shell input or volume keys if the verified hidden signature is unavailable.
 */
object CameraShutterMain {
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(Process.myUid() == 0 && Build.VERSION.SDK_INT == 36 && Build.MODEL == "CPH2745" &&
                Build.DISPLAY == "CPH2745_16.0.10.601(EX01)")
            require(args.size == 1)
            val uid = args.single().toInt(); require(uid in 10000..99999)
            val type = Class.forName("android.hardware.input.InputManagerGlobal")
            val manager = type.getDeclaredMethod("getInstance").invoke(null)
            val inject = type.getDeclaredMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            require(inject.returnType == Boolean::class.javaPrimitiveType)
            val down = SystemClock.uptimeMillis()
            fun event(action: Int) = KeyEvent(down, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_CAMERA,
                0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
            val sentDown = inject.invoke(manager, event(KeyEvent.ACTION_DOWN), 2, uid) == true
            if (!sentDown) exitProcess(1)
            // One UP, no repeat or fallback injection.
            val sentUp = inject.invoke(manager, event(KeyEvent.ACTION_UP), 2, uid) == true
            if (!sentUp) exitProcess(1)
            println("SHUTTER_SENT")
            exitProcess(0)
        } catch (_: Throwable) { exitProcess(1) }
    }
}
