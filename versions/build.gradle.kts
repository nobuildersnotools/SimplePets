import org.bsdevelopment.spigotweight.extension.UserdevExtension

plugins {
    id("org.bsdevelopment.java-conventions")
    alias(libs.plugins.spigotweight)
}
var latestMinecraft = "26.3"

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":main"))
    compileOnly(libs.pluginutils)
}

spigotweight {
    minecraftVersion = latestMinecraft
    target = UserdevExtension.SpigotAPITarget.SPIGOT   // SPIGOT or PAPER
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

