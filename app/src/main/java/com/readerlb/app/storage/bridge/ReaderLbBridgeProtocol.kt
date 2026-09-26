package com.readerlb.app.storage.bridge

internal object ReaderLbBridgeProtocol {
    const val MAGIC = 0x524C4231 // RLB1
    const val VERSION = 1

    const val OP_PING = "ping"
    const val OP_LIST = "list"
    const val OP_LIST_META = "list_meta"
    const val OP_EXISTS = "exists"
    const val OP_IS_DIRECTORY = "is_directory"
    const val OP_LENGTH = "length"
    const val OP_LAST_MODIFIED = "last_modified"
    const val OP_CREATE = "create"
    const val OP_DELETE = "delete"
    const val OP_RENAME = "rename"
    const val OP_READ = "read"
    const val OP_WRITE = "write"
    const val OP_READ_AT = "read_at"
    const val OP_WRITE_AT = "write_at"
    const val OP_TRUNCATE = "truncate"
    const val OP_FSYNC = "fsync"

    const val MAX_RELATIVE_PATH_BYTES = 8 * 1024
    const val MAX_IO_CHUNK_BYTES = 1024 * 1024
    const val MAX_LIST_ENTRIES = 100_000
}
