package dev.viaduct.persistence.io

import java.io.File

/** Creates a directory when needed and fails with context if the filesystem rejects it. */
internal fun File.ensureDirectory() {
    if (!isDirectory) {
        check(mkdirs()) { "Could not create directory $absolutePath" }
    }
}

/** Ensures that the directory containing this file exists. */
internal fun File.ensureParentDirectory() {
    parentFile?.ensureDirectory()
}
