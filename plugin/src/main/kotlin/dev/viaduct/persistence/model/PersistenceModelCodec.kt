package dev.viaduct.persistence.model

import java.io.File

/** Compatibility facade for the separate semantic-model reader and writer. */
object PersistenceModelCodec {
    fun write(model: PersistenceModel, destination: File) =
        PersistenceModelWriter.write(model, destination)

    fun read(source: File): PersistenceModel = PersistenceModelReader.read(source)
}
