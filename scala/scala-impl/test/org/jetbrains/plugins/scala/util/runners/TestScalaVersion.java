package org.jetbrains.plugins.scala.util.runners;

import org.jetbrains.plugins.scala.LatestScalaVersions;

// required at compile time to use in annotations
public enum TestScalaVersion {

    Scala_2_10_0(false), Scala_2_10,
    Scala_2_11_0(false), Scala_2_11,
    Scala_2_12_0(false), Scala_2_12_6, Scala_2_12_12, Scala_2_12,
    Scala_2_13_0, Scala_2_13,
    Scala_3_0,
    Scala_3_1,
    Scala_3_2,
    Scala_3_Latest
    ;

    public final boolean supportsJdk11;

    TestScalaVersion(boolean supportsJdk11) {
        this.supportsJdk11 = supportsJdk11;
    }

    TestScalaVersion() {
        this(true);
    }

    public boolean supportsJdk(TestJdkVersion jdkVersion) {
        return jdkVersion != TestJdkVersion.JDK_11 || supportsJdk11;
    }

    public org.jetbrains.plugins.scala.ScalaVersion toProductionVersion() {
        return switch (this) {
            case Scala_2_10 -> LatestScalaVersions.Scala_2_10();
            case Scala_2_11 -> LatestScalaVersions.Scala_2_11();
            case Scala_2_12 -> LatestScalaVersions.Scala_2_12();
            case Scala_2_12_6 -> LatestScalaVersions.Scala_2_12().withMinor(6);
            case Scala_2_12_12 -> LatestScalaVersions.Scala_2_12().withMinor(12);
            case Scala_2_13 -> LatestScalaVersions.Scala_2_13();
            case Scala_2_10_0 -> LatestScalaVersions.Scala_2_10().withMinor(0);
            case Scala_2_11_0 -> LatestScalaVersions.Scala_2_11().withMinor(0);
            case Scala_2_12_0 -> LatestScalaVersions.Scala_2_12().withMinor(0);
            case Scala_2_13_0 -> LatestScalaVersions.Scala_2_13().withMinor(0);
            case Scala_3_0 -> LatestScalaVersions.Scala_3_0();
            case Scala_3_1 -> LatestScalaVersions.Scala_3_1();
            case Scala_3_2 -> LatestScalaVersions.Scala_3_2();
            case Scala_3_Latest -> LatestScalaVersions.Scala_3();
        };
    }
}
