package dev.opluskeycontrol.ui

import dev.opluskeycontrol.R
import dev.opluskeycontrol.domain.*
import dev.opluskeycontrol.discovery.Launchability

fun Button.label() = when(this) { Button.PLUS -> R.string.plus; Button.VOLUME_UP -> R.string.volume_up; Button.VOLUME_DOWN -> R.string.volume_down; Button.POWER -> R.string.power }
fun Gesture.label() = when(this) { Gesture.SINGLE -> R.string.single; Gesture.DOUBLE -> R.string.double_click; Gesture.TRIPLE -> R.string.triple_click; Gesture.LONG -> R.string.long_press; Gesture.CHORD -> R.string.chord }
fun ActionKind.label() = when(this) { ActionKind.TORCH_TOGGLE -> R.string.torch_toggle; ActionKind.TORCH_ON -> R.string.torch_on; ActionKind.TORCH_OFF -> R.string.torch_off; ActionKind.APP -> R.string.open_app; ActionKind.ACTIVITY -> R.string.open_activity; ActionKind.STOCK_CAMERA_CAPTURE -> R.string.stock_camera; ActionKind.RECORD_TOGGLE -> R.string.recorder_toggle }
fun Tier.label() = when(this) { Tier.BASIC -> R.string.basic; Tier.SHIZUKU -> R.string.shizuku; Tier.ROOT -> R.string.root }
fun Launchability.label() = when(this) { Launchability.LAUNCHER -> R.string.launcher_activity; Launchability.EXPORTED_UNVERIFIED -> R.string.exported_unverified; Launchability.INTERNAL -> R.string.internal_activity; Launchability.PROTECTED -> R.string.protected_activity; Launchability.DISABLED -> R.string.disabled_activity }
fun minuteLabel(minute: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)
