plugins {
  base
}

val defaultVersion = property("version").toString()
val resolvedVersion =
    if (System.getenv("GITHUB_REF_TYPE") == "tag") {
      System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }?.removePrefix("v")
          ?: defaultVersion
    } else {
      defaultVersion
    }

allprojects {
  version = resolvedVersion
}
