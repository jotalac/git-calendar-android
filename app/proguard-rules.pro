# R8 rules for the release build.

# gomobile: the Go side of libgojni.so calls these classes through JNI by name, so R8 must not
# rename or remove them. gcal.aar ships the same rules; they are repeated here in case the
# local AAR's consumer rules are skipped.
-keep class go.** { *; }
-keep class dev.jotalac.core.gcal.** { *; }
