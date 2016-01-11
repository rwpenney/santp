name := "santp"

import android.Keys._
android.Plugin.androidBuild

scalaVersion := "2.11.7"
scalacOptions ++= Seq("-deprecation", "-feature")


proguardOptions in Android ++= Seq(
    "-dontobfuscate",
    "-dontoptimize",
    "-dontwarn sun.misc.Unsafe",
    "-dontwarn scala.collection.**",
    "-keepattributes EnclosingMethod",
    "-keepattributes InnerClasses",
    "-keepattributes Signature",
    "-keepclasseswithmembers class * { public <init>(akka.actor.ExtendedActorSystem); }",
    "-keepclasseswithmembers class * { public <init>(com.typesafe.config.Config, akka.event.LoggingAdapter, java.util.concurrent.ThreadFactory); }",
    "-keepclasseswithmembers class * { public <init>(java.lang.String, akka.actor.ActorSystem$Settings, akka.event.EventStream, akka.actor.DynamicAccess); }",
    "-keep class akka.**",
    "-keep class akka.actor.LocalActorRefProvider$Guardian { *; }",
    "-keep class akka.actor.LocalActorRefProvider$SystemGuardian { *; }",
    "-keep class akka.actor.RepointableActorRef { *; }",
    "-keep class akka.dispatch.UnboundedMailbox { *; }",
    "-keep class com.typesafe.**",
    "-keep class scala.collection.immutable.*",
    "-keep class scala.collection.mutable.WrappedArray",
    "-keep class scala.Option",
    "-keep class scala.PartialFunction",
    "-keep class scala.concurrent.duration.*",
    "-keep class uk.rwpenney.santp.*",
    "-keep class uk.rwpenney.santp.NTPtimeRef { *; }",
    "-keep class uk.rwpenney.santp.UIupdater { *; }",
    "-keep class uk.rwpenney.santp.TimeRefFuser { *; }",
    "-printseeds target/seeds.txt", "-printusage target/usage.txt"
)


libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.14",
    "commons-net" % "commons-net" % "3.4",
    "net.sf.proguard" % "proguard-base" % "5.1"
)


run <<= run in Android
install <<= install in Android
