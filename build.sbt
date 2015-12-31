name := "santp"

import android.Keys._
android.Plugin.androidBuild

scalaVersion := "2.11.7"
proguardCache in Android ++= Seq("org.scaloid")

proguardOptions in Android ++= Seq(
    "-dontobfuscate", "-dontoptimize", "-keepattributes Signature",
    "-printseeds target/seeds.txt", "-printusage target/usage.txt",
    "-dontwarn scala.collection.**", "-dontwarn org.scaloid.**"
)

libraryDependencies ++= Seq(
    "org.scaloid" %% "scaloid" % "4.1",
    "net.sf.proguard" % "proguard-base" % "5.1"
)

run <<= run in Android
install <<= install in Android
