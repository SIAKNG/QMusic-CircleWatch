-keepattributes *Annotation*
-keep class com.ronan.qmusicwatch.model.** { *; }

# These classes are supplied by OPPO firmware, not the public Android SDK.
# OppoRoundSwitch checks both class availability and the system service before
# inflating HeySwitch; other devices use the Compose fallback.
-dontwarn android.os.linearmotorvibrator.LinearmotorVibrator
-dontwarn android.os.linearmotorvibrator.WaveformEffect$Builder
-dontwarn android.os.linearmotorvibrator.WaveformEffect

