name := "santp"

import android.Keys._

enablePlugins(AndroidApp)

scalaVersion := "2.11.12"
scalacOptions ++= Seq("-deprecation", "-feature")
javacOptions ++= Seq("-source", "1.7", "-target", "1.7")


proguardOptions in Android ++= Seq(
    "-dontobfuscate",
    "-dontoptimize",
    "-dontwarn android.annotation.TargetApi",
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
    "-keep class scala.collection.Seq",
    "-keep class scala.collection.mutable.WrappedArray",
    "-keep class scala.concurrent.duration.*",
    "-keep class scala.math.Ordering$Double$",
    "-keep class scala.Option",
    "-keep class scala.PartialFunction",
    "-keep class uk.rwpenney.santp.*",
    "-keep class uk.rwpenney.santp.GeoLocator { *; }",
    "-keep class uk.rwpenney.santp.NTPtimeRef { *; }",
    "-keep class uk.rwpenney.santp.TimeRefFuser { *; }",
    "-keep class uk.rwpenney.santp.UIupdater { *; }",
    "-libraryjars /usr/lib/jvm/default-java/jre/lib/rt.jar",
    "-printseeds target/seeds.txt", "-printusage target/usage.txt"
)


libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.3.16",   // Last to support Java 7
    "commons-net" % "commons-net" % "3.6",
    "net.sf.proguard" % "proguard-base" % "5.3"
)
