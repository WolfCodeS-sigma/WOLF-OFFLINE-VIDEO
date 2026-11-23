import korlibs.korge.gradle.*

plugins {
    alias(libs.plugins.korge)
}

korge {
    id = "com.wolfcodes.stage11.korgepoc"
    androidSdk(compileSdk = 35, minSdk = 26, targetSdk = 35)
    targetAndroid()
}
