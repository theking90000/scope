plugins {
    java
    application
}

group = "be.theking90000"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("be.theking90000:scope")

    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    implementation("org.openjdk.jol:jol-core:0.17")
}

application {
    mainClass.set("be.theking90000.scope.benchmarks.BenchmarkMain")
}

tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run synthetic JMH benchmarks for scope"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("be.theking90000.scope.benchmarks.BenchmarkMain")

    val resultFormat = providers.gradleProperty("jmhResultFormat").orElse("JSON")
    val resultFile = providers.gradleProperty("jmhResultFile")
        .orElse(layout.buildDirectory.file("reports/jmh/results.${resultFormat.get().lowercase()}").map { it.asFile.path })

    args(
        "--result-format", resultFormat.get(),
        "--result", resultFile.get()
    )

    doFirst {
        file(resultFile.get()).parentFile.mkdirs()
    }
}

tasks.register<JavaExec>("memoryReport") {
    group = "benchmark"
    description = "Measure approximate retained heap and JOL footprints"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("be.theking90000.scope.benchmarks.MemoryReport")

    // Keep measurements reproducible and make 100k scenarios feasible.
    jvmArgs(
        "-Xms2g",
        "-Xmx2g",
        "-XX:+UseG1GC",
        "-Djdk.attach.allowAttachSelf=true",
        "-Djol.magicFieldOffset=true"
    )
}
