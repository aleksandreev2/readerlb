package com.readerlb.app.shizuku

import android.os.ParcelFileDescriptor
import androidx.annotation.Keep
import java.io.File

/**
 * Runs inside Shizuku/Sui with shell or root identity.
 *
 * Security boundary: every operation is restricted to RanobeLib's public
 * external app-specific local-book directory. ReaderLB cannot use this
 * service as a general privileged file manager.
 */
@Keep
class RanobeLibPrivilegedService :
    IRanobeLibPrivilegedService.Stub() {

    @Keep
    constructor()

    override fun destroy() {
        System.exit(0)
    }

    override fun rootExists(): Boolean =
        ROOT.isDirectory

    override fun exists(
        relativePath: String
    ): Boolean =
        target(relativePath)
            .exists()

    override fun isDirectory(
        relativePath: String
    ): Boolean =
        target(relativePath)
            .isDirectory

    override fun listNames(
        relativePath: String
    ): Array<String> =
        target(relativePath)
            .takeIf(File::isDirectory)
            ?.list()
            ?.sorted()
            ?.toTypedArray()
            ?: emptyArray()

    override fun length(
        relativePath: String
    ): Long =
        target(relativePath)
            .takeIf(File::isFile)
            ?.length()
            ?: -1L

    override fun mkdirs(
        relativePath: String
    ): Boolean {
        requireNonRoot(
            relativePath
        )
        val directory =
            target(relativePath)

        return directory.isDirectory ||
            directory.mkdirs()
    }

    override fun deleteRecursively(
        relativePath: String
    ): Boolean {
        requireNonRoot(
            relativePath
        )
        val value =
            target(relativePath)

        return !value.exists() ||
            value.deleteRecursively()
    }

    override fun rename(
        fromRelativePath: String,
        toRelativePath: String
    ): Boolean {
        requireNonRoot(
            fromRelativePath
        )
        requireNonRoot(
            toRelativePath
        )

        val from =
            target(
                fromRelativePath
            )
        val to =
            target(
                toRelativePath
            )

        if (
            !from.exists() ||
            to.exists()
        ) {
            return false
        }

        val parent =
            to.parentFile
                ?: return false

        if (
            !parent.isDirectory
        ) {
            return false
        }

        return from.renameTo(to)
    }

    override fun openRead(
        relativePath: String
    ): ParcelFileDescriptor {
        requireNonRoot(
            relativePath
        )
        val file =
            target(relativePath)
        require(
            file.isFile
        ) {
            "Файл не найден"
        }

        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor
                .MODE_READ_ONLY
        )
    }

    override fun openWrite(
        relativePath: String
    ): ParcelFileDescriptor {
        requireNonRoot(
            relativePath
        )
        val file =
            target(relativePath)
        val parent =
            file.parentFile
                ?: error(
                    "Некорректный путь"
                )

        require(
            parent.isDirectory
        ) {
            "Папка назначения не существует"
        }

        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor
                .MODE_CREATE or
                ParcelFileDescriptor
                    .MODE_TRUNCATE or
                ParcelFileDescriptor
                    .MODE_WRITE_ONLY
        )
    }

    private fun target(
        relativePath: String
    ): File =
        resolveRestrictedRanobeLibPath(
            root = ROOT_CANONICAL,
            relativePath =
                relativePath
        )

    private fun requireNonRoot(
        relativePath: String
    ) {
        require(
            relativePath
                .trim()
                .trim('/', '\\')
                .isNotEmpty()
        ) {
            "Операция с корнем библиотеки запрещена"
        }
    }

    private companion object {
        val ROOT =
            File(
                "/storage/emulated/0/" +
                    "Android/data/" +
                    "ru.libappc/files/book"
            )
        val ROOT_CANONICAL =
            ROOT.canonicalFile
    }
}


internal fun resolveRestrictedRanobeLibPath(
    root: File,
    relativePath: String
): File {
    require(
        !relativePath
            .startsWith("/") &&
            !relativePath
                .startsWith("\\") &&
            '\u0000' !in
                relativePath
    ) {
        "Разрешён только относительный путь внутри библиотеки RanobeLib"
    }

    val clean =
        relativePath
            .replace(
                '\\',
                '/'
            )
            .trim()
            .trim('/')

    val canonicalRoot =
        root.canonicalFile
    val candidate =
        if (
            clean.isEmpty()
        ) {
            canonicalRoot
        } else {
            File(
                canonicalRoot,
                clean
            ).canonicalFile
        }

    val rootPath =
        canonicalRoot.path
    val candidatePath =
        candidate.path

    require(
        candidatePath ==
            rootPath ||
            candidatePath.startsWith(
                rootPath +
                    File.separator
            )
    ) {
        "Путь выходит за пределы библиотеки RanobeLib"
    }

    return candidate
}
