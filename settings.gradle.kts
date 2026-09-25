plugins {
    // 允许 Gradle 按构建要求自动拉取匹配的 JDK 工具链（构建声明 Java 25），新环境无需预装。
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "FoxSkinServer"
