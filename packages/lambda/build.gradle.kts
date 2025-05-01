plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":common"))
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.amazonaws:aws-lambda-java-events:3.11.3")
    implementation("org.apache.logging.log4j:log4j-api:2.22.1")
    implementation("org.apache.logging.log4j:log4j-core:2.22.1")
    runtimeOnly("com.amazonaws:aws-lambda-java-log4j2:1.6.0")
    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.parameternames)
    testImplementation(libs.junit.jupiter)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

// このプラグインはまだ tasks.shadowJar という設定ができず、古い置き換え前のライブラリの実装をしないといけないらしい
// 参考: https://gradleup.com/shadow/kotlin-plugins/
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    // デフォルトでは <name>.jar と 依存関係も含んだ <name>-all.jar が作成される
    // IDE や テストで軽量な jar を使用する場合はデフォルト設定にするが、今回は活用しないので <name>.jar として上書きする
    archiveClassifier.set("")
    // AWS SDK v2 では HTTPクライアントや認証プロバイダーなどを ServiceLoader で切り替える設定になっている
    // FatJar では同じサービス記述が複数あれば最後の1つしか残らないため、連携マージをしてランタイムの ServiceLoader の完全な実装一覧を見れるようにする
    mergeServiceFiles()
}