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
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1") // slf4j は使用しないが警告が出るため実装を追加
    runtimeOnly("org.apache.logging.log4j:log4j-layout-template-json:2.22.1")
    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.parameternames)
    testImplementation(libs.junit.jupiter)

    // テスト用 SLF4J API と Log4j2 バインディング
    testImplementation("org.slf4j:slf4j-api:1.7.36")
    testRuntimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.22.1")

    // Mockito for unit tests
    testImplementation("org.mockito:mockito-core:4.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")

    // テスト作成のために追加したライブラリ
    // Testcontainers BOM
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.6"))
    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    // Testcontainers Core + JUnit5
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")
    // LocalStack モジュール
    testImplementation("org.testcontainers:localstack")
    // Awaitility (非同期検証用)
    testImplementation("org.awaitility:awaitility:4.2.0")
    // AWS SDK v2 for SQS & Lambda
    implementation("software.amazon.awssdk:sqs:2.31.33")
    implementation("software.amazon.awssdk:lambda:2.31.33")
    // AWS SDK v2 for CloudWatch Logs (テストでログ取得用)
    implementation("software.amazon.awssdk:cloudwatchlogs:2.31.33")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

// このプラグインはまだ tasks.shadowJar という設定ができず、古い置き換え前のライブラリの実装をしないといけないらしい
// 参考: https://gradleup.com/shadow/kotlin-plugins/
val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    // デフォルトでは <name>.jar と 依存関係も含んだ <name>-all.jar が作成される
    // IDE や テストで軽量な jar を使用する場合はデフォルト設定にするが、今回は活用しないので <name>.jar として上書きする
    archiveClassifier.set("")
    // AWS SDK v2 では HTTPクライアントや認証プロバイダーなどを ServiceLoader で切り替える設定になっている
    // FatJar では同じサービス記述が複数あれば最後の1つしか残らないため、連携マージをしてランタイムの ServiceLoader の完全な実装一覧を見れるようにする
    mergeServiceFiles()
}

tasks.test {
    dependsOn(shadowJar)
    // テスト実行直前に JAR ファイルの絶対パスを取得して設定
    val jarFile = shadowJar.get().archiveFile.get().asFile.absolutePath
    systemProperty("lambda.jar.path", jarFile)
}